package com.FINAL.KIP.user.service;

import com.FINAL.KIP.common.CommonResponse;
import com.FINAL.KIP.securities.JwtTokenProvider;
import com.FINAL.KIP.securities.refresh.UserRefreshToken;
import com.FINAL.KIP.securities.refresh.UserRefreshTokenRepository;
import com.FINAL.KIP.user.domain.User;
import com.FINAL.KIP.user.dto.req.CreateUserReqDto;
import com.FINAL.KIP.user.dto.req.LoginReqDto;
import com.FINAL.KIP.user.dto.res.UserResDto;
import com.FINAL.KIP.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder; //비밀번호 암호화
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRefreshTokenRepository userRefreshTokenRepository;

    @Autowired
    public UserService(UserRepository userRepo, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider, UserRefreshTokenRepository userRefreshTokenRepository) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRefreshTokenRepository = userRefreshTokenRepository;
    }

//    Create
    public UserResDto createUser(CreateUserReqDto dto) {
        dto.setPassword(passwordEncoder.encode(dto.makeUserReqDtoToUser().getPassword())); // 비밀번호 암호화
        User user = dto.makeUserReqDtoToUser();
        User savedUser = userRepo.save(user);
        return new UserResDto(savedUser);
    }

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
    public List<UserResDto> getAllUsers() {
        List<User> users = userRepo.findAll();
        return users.stream()
                .map(UserResDto::new)
                .collect(Collectors.toList());
    }
//    함수공통화
    public User getUserById(Long id) {
        return userRepo.findById(id).orElse(null);
    }

    public CommonResponse login(LoginReqDto loginReqDto) throws IllegalArgumentException{
        User user = userRepo.findByEmployeeId(loginReqDto.getEmployeeId())
                .filter(
                        kip -> passwordEncoder.matches(loginReqDto.getPassword(), kip.getPassword()))
                .orElseThrow(() -> new IllegalArgumentException("사원번호 또는 비밀번호가 일치하지 않습니다."));

        String accessToken = jwtTokenProvider.createAccessToken(
                String.format("%s:%s", user.getId(), user.getRole()));

        String refreshToken = jwtTokenProvider.createRefreshToken();
        userRefreshTokenRepository.findById(user.getId())
                .ifPresentOrElse(
                        kip -> kip.updateUserRefreshToken(refreshToken),
                        () -> userRefreshTokenRepository.save(new UserRefreshToken(user, refreshToken))
                );
        Map<String, String> result = new HashMap<>();
        result.put("access_token", accessToken);
        result.put("refresh_token", refreshToken);
        return new CommonResponse(HttpStatus.OK, "JWT token is created!", result);
    }
}