package com.FINAL.KIP.user.service;

import com.FINAL.KIP.bookmark.repository.BookRepository;
import com.FINAL.KIP.common.CommonResponse;
import com.FINAL.KIP.common.aspect.UserAdmin;
import com.FINAL.KIP.common.firebase.FCMTokenDao;
import com.FINAL.KIP.common.s3.S3Config;
import com.FINAL.KIP.document.dto.res.AgreeDocResDto;
import com.FINAL.KIP.request.domain.Request;
import com.FINAL.KIP.request.repository.RequestRepository;
import com.FINAL.KIP.securities.JwtTokenProvider;
import com.FINAL.KIP.securities.refresh.UserRefreshToken;
import com.FINAL.KIP.securities.refresh.UserRefreshTokenRepository;
import com.FINAL.KIP.user.domain.User;
import com.FINAL.KIP.user.dto.req.CreateUserReqDto;
import com.FINAL.KIP.user.dto.req.LoginReqDto;
import com.FINAL.KIP.user.dto.req.UserInfoUpdateReqDto;
import com.FINAL.KIP.user.dto.res.UserResDto;
import com.FINAL.KIP.user.repository.UserRepository;
import com.amazonaws.services.s3.model.ObjectMetadata;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder; //비밀번호 암호화
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRefreshTokenRepository userRefreshTokenRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final FCMTokenDao fcmTokenDao;
    private final RequestRepository requestRepository;

    //s3 연결 config
    private final S3Config s3Config;

    // s3 bucket 이름
    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Autowired
    public UserService(UserRepository userRepo, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider, UserRefreshTokenRepository userRefreshTokenRepository, BookRepository bookRepository, UserRepository userRepository,
		FCMTokenDao fcmTokenDao, RequestRepository requestRepository, S3Config s3Config) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRefreshTokenRepository = userRefreshTokenRepository;
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
		this.fcmTokenDao = fcmTokenDao;
		this.requestRepository = requestRepository;
		this.s3Config = s3Config;
	}

//    Create
    @UserAdmin
    public UserResDto createUser(CreateUserReqDto dto) {
        dto.setPassword(passwordEncoder.encode(dto.makeUserReqDtoToUser().getPassword())); // 비밀번호 암호화
        User user = dto.makeUserReqDtoToUser();
        User savedUser = userRepo.save(user);
        return new UserResDto(savedUser);
    }

    @UserAdmin
    public List<UserResDto> createUsers(List<CreateUserReqDto> dtos) {
        for (CreateUserReqDto createUserReqDto : dtos) {
            createUserReqDto.setPassword(
                passwordEncoder.encode(createUserReqDto.makeUserReqDtoToUser().getPassword()));
        }
        return dtos.stream()
            .map(CreateUserReqDto::makeUserReqDtoToUser)
            .map(userRepo::save)
            .map(UserResDto::new)
            .collect(Collectors.toList());
    }

//    Read
    @UserAdmin
    public List<UserResDto> getAllUsers() {
        List<User> users = userRepo.findAll();
        return users.stream()
                .map(UserResDto::new)
                .collect(Collectors.toList());
    }

    @UserAdmin
    public User getUserById(Long id) {
        return userRepo.findById(id).orElse(null);
    }

    public CommonResponse login(LoginReqDto loginReqDto) throws IllegalArgumentException{
        User user = userRepo.findByEmployeeId(loginReqDto.getEmployeeId())
                .filter(
                        kip -> passwordEncoder.matches(loginReqDto.getPassword(), kip.getPassword()))
                .orElseThrow(() -> new IllegalArgumentException("사원번호 또는 비밀번호가 일치하지 않습니다."));

        String accessToken = jwtTokenProvider.createAccessToken(
                String.format("%s:%s", user.getEmployeeId(), user.getRole()));

        String refreshToken = jwtTokenProvider.createRefreshToken();
        userRefreshTokenRepository.findById(user.getId())
                .ifPresentOrElse(
                        kip -> kip.updateUserRefreshToken(refreshToken),
                        () -> userRefreshTokenRepository.save(new UserRefreshToken(user, refreshToken))
                );
        Map<String, String> result = new HashMap<>();
        result.put("user_name", user.getName());
        result.put("access_token", accessToken);
        result.put("refresh_token", refreshToken);
        return new CommonResponse(HttpStatus.OK, "JWT token is created!", result);
    }

    @UserAdmin
    public CommonResponse logout() {
        User user = getUserFromAuthentication();
        userRefreshTokenRepository.deleteById(user.getId());
        fcmTokenDao.deleteToken(user.getEmployeeId());
        return new CommonResponse(HttpStatus.OK, "User Logout SUCCESS!", new UserResDto(user));
    }

    @UserAdmin
    public CommonResponse mypage(){
        User userInfo = getUserFromAuthentication(); // 순환참조 생겨서 dto로 변경 (세종)
        UserResDto userResDto = new UserResDto(userInfo);
        return new CommonResponse(HttpStatus.OK, "User info loaded successfully!", userResDto);
    }

    @Transactional
    @UserAdmin
    public void update(UserInfoUpdateReqDto userInfoUpdateReqDto){
        User userInfo = getUserFromAuthentication();
        userInfo.updateUserInfo(userInfoUpdateReqDto.getName(), userInfoUpdateReqDto.getEmail(),
                userInfoUpdateReqDto.getPhoneNumber());
    }

    // 백엔드에서 비밀번호 검증 로직
    public boolean validateCurrentPassword(String inputPassword) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String employeeId = authentication.getName();
        User user = userRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new EntityNotFoundException("User not found."));
        return passwordEncoder.matches(inputPassword, user.getPassword());
    }

    // 비밀번호 변경
    @Transactional
    public boolean changePassword(String employeeId, String currentPassword, String newPassword) {
        User user = userRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        return true;
    }

    @Transactional
    @UserAdmin
    public void delete(String employeeId){
        if(employeeId.equals("k-1234567890"))
            throw new IllegalArgumentException("관리자 계정은 삭제 불가합니다.");
        User userInfo = getUserByEmployeeId(employeeId);
        userRepo.delete(userInfo);
    }

//    ===== 함수 공통화 =====
    @UserAdmin
    public User getUserFromAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return getUserByEmployeeId(authentication.getName());
    }

    public User getUserByEmployeeId(String employeeId) {
        return userRepo.findByEmployeeId(employeeId)
                .orElseThrow(() -> new EntityNotFoundException("사원번호를 찾을 수 없습니다. " + employeeId));
    }

    // 현재 인증된 사용자 정보 조회
    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String employeeId = authentication.getName();
        return userRepo.findByEmployeeId(employeeId)
                .orElseThrow(() -> new EntityNotFoundException("인증된 사용자를 찾을 수 없습니다."));
    }

//    ===== 프로필 이미지 =====

    // 프로필 이미지 업로드 S3
    public String uploadImage(MultipartFile file) throws IOException {
        User user = getAuthenticatedUser();
        String originalFilename = file.getOriginalFilename();

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(file.getContentType());

        user.setProfileImageUrl(s3Config.amazonS3Client().getUrl(bucket, originalFilename).toString());

        userRepo.save(user);

        s3Config.amazonS3Client().putObject(bucket, originalFilename, file.getInputStream(), metadata);
        return s3Config.amazonS3Client().getUrl(bucket, originalFilename).toString();
    }

    // 프로필 이미지 조회
    public String getProfileImageUrl() {
        User user = getAuthenticatedUser();
        return Optional.ofNullable(user.getProfileImageUrl())
                .orElseThrow(() -> new EntityNotFoundException("프로필 이미지가 설정되지 않았습니다."));
    }

//    public String uploadImage(MultipartFile file) throws IOException {
//        String originalFilename = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
//        String fileExtension = StringUtils.getFilenameExtension(originalFilename);
//
//        // S3 버킷 내 파일명 중복을 방지하기 위한 UUID 추가
//        String fileName = UUID.randomUUID().toString() + "." + fileExtension;
//
//        ObjectMetadata metadata = new ObjectMetadata();
//        metadata.setContentLength(file.getSize());
//        metadata.setContentType(file.getContentType());
//
//        // 파일을 S3에 업로드
//        s3Config.amazonS3Client().putObject(bucket, originalFilename, file.getInputStream(), metadata);
////        s3Config.amazonS3Client().putObject(new PutObjectRequest(bucket, fileName, file.getInputStream(), metadata)
////                .withCannedAcl(CannedAccessControlList.PublicRead)); // 파일을 public으로 설정
//
//        // 업로드된 파일의 URL을 반환
//        return s3Config.amazonS3Client().getUrl(bucket, fileName).toString();
//    }


//    // 프로필 이미지 업로드
//    @Transactional
//    public ProfileImageResDto uploadProfileImage(MultipartFile file) throws IOException {
//        User user = getAuthenticatedUser();
//
//        String originalFilename = file.getOriginalFilename();
//        String fileExtension = Objects.requireNonNull(originalFilename).substring(originalFilename.lastIndexOf("."));
//        String storedFileName = UUID.randomUUID().toString() + fileExtension;
//
//        Path destinationFile = Paths.get(uploadDir, originalFilename).toAbsolutePath().normalize();
//        Files.createDirectories(destinationFile.getParent());
//
//        Files.copy(file.getInputStream(), destinationFile, StandardCopyOption.REPLACE_EXISTING);
//
//        user.setProfileImageUrl(destinationFile.toString());
//        userRepo.save(user);
//
//        return new ProfileImageResDto(user.getId(), user.getProfileImageUrl());
//    }
//
//    // 프로필 이미지 조회
//    public String getProfileImageUrl() {
//        User user = getAuthenticatedUser();
//        return Optional.ofNullable(user.getProfileImageUrl())
//                .orElseThrow(() -> new EntityNotFoundException("프로필 이미지가 설정되지 않았습니다."));
//    }
//
//    // 프로필 이미지 삭제
//    @Transactional
//    public String deleteProfileImage() {
//        User user = getAuthenticatedUser();
//        if (user.getProfileImageUrl() == null || user.getProfileImageUrl().isEmpty()) {
//            return "프로필 이미지가 없습니다!";
//        } else {
//            try {
//                Path path = Paths.get(user.getProfileImageUrl());
//                Files.deleteIfExists(path);
//                user.setProfileImageUrl(null);
//                userRepo.save(user);
//                return "프로필 이미지가 성공적으로 삭제되었습니다.";
//            } catch (IOException e) {
//                return "프로필 이미지 삭제에 실패했습니다.";
//            }
//        }
//    }
//
//    // 프로필 이미지 업데이트
//    @Transactional
//    public ProfileImageResDto updateProfileImage(MultipartFile file) throws IOException {
//        User user = getAuthenticatedUser();
//
//        if (user.getProfileImageUrl() == null || user.getProfileImageUrl().isEmpty()) {
//            throw new EntityNotFoundException("수정할 프로필 이미지가 없습니다. 프로필 이미지를 먼저 올려 주세요.");
//        }
//
//        // 기존 이미지 삭제
//        Path oldImagePath = Paths.get(user.getProfileImageUrl());
//        Files.deleteIfExists(oldImagePath);
//
//        // 새 이미지 저장
//        String originalFilename = file.getOriginalFilename();
//        String fileExtension = Objects.requireNonNull(originalFilename).substring(originalFilename.lastIndexOf("."));
//        String storedFileName = UUID.randomUUID() + fileExtension;
//
//        Path destinationFile = Paths.get(uploadDir, originalFilename).toAbsolutePath().normalize();
//        Files.createDirectories(destinationFile.getParent());
//        Files.copy(file.getInputStream(), destinationFile, StandardCopyOption.REPLACE_EXISTING);
//
//        user.setProfileImageUrl(destinationFile.toString());
//        userRepo.save(user);
//
//        return new ProfileImageResDto(user.getId(), user.getProfileImageUrl());
//    }


    // 로그인 이전에 기본 정보 체크하는 함수들
    public Boolean checkIfEmployeeIdExists(String employeeId) {
        return userRepo.existsByEmployeeId(employeeId);
    }

    public Map<String, Object> checkIdPassAndReturnName(LoginReqDto dto) {
        Map<String, Object> passwordValidAndUserName = new HashMap<>();
        User user = getUserByEmployeeId(dto.getEmployeeId());
        passwordValidAndUserName.put("userName", user.getName());
        if (passwordEncoder.matches(dto.getPassword(), user.getPassword()))
            passwordValidAndUserName.put("isValid", true);
        else passwordValidAndUserName.put("isValid", false);
        return passwordValidAndUserName;
    }

    public Boolean checkIfPhoneNumberExists(String phoneNumber) {
        return userRepo.existsByPhoneNumber(phoneNumber);
    }

    public Boolean checkIfEmailExists(String email) {
        return userRepo.existsByEmail(email);
    }

	public ResponseEntity<List<AgreeDocResDto>> getAgreeDocs() {
        User user = getAuthenticatedUser();

        List<AgreeDocResDto> collect = requestRepository.findAgreedRequest(user).stream()
            .map(Request::getDocument)
            .map(document -> {
                return new AgreeDocResDto(
                    document.getId(),
                    document.getTitle(),
                    document.getGroup().getGroupName()
                );
            }).toList();

        return new ResponseEntity<>(collect, HttpStatus.OK);
    }
}