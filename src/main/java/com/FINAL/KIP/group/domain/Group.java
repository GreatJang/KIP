package com.FINAL.KIP.group.domain;

import com.FINAL.KIP.common.domain.BaseEntity;
import com.FINAL.KIP.document.domain.Document;
import com.FINAL.KIP.document.domain.KmsDocType;
import com.FINAL.KIP.request.domain.Request;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
@Entity
@Table(name = "\"group\"") // group이 mariaDb예약어라 설정해줌.
public class Group extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false)
    private String groupName;

    @Setter
    @Enumerated(value = EnumType.STRING)
    @Column(nullable = false)
    private GroupType groupType;

    @Setter
    @ManyToOne
    @JoinColumn
    private Group superGroup;

    public Group() {}

    @OneToMany(mappedBy = "superGroup", cascade = CascadeType.PERSIST)
    private final List<Group> childGroups = new ArrayList<>();

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL)
    private final List<Document> documents = new ArrayList<>();

    @OneToMany(mappedBy = "group", cascade = CascadeType.REMOVE)
    private final List<GroupUser> groupUsers = new ArrayList<>();

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL)
    private final List<Request> requests = new ArrayList<>();

    @Builder
    public Group(String groupName, GroupType groupType) {
        this.groupName = groupName;
        this.groupType = groupType;
    }

    @PrePersist
    public void prePersist() {
        documents.add(
                Document.builder()
                        .title("empty")
                        .kmsDocType(KmsDocType.SECTION)
                        .group(this)
                        .build()
        );
    }

    // 그룹 이동시 하위그룹 아이디를 피하기위한 정보를 제공하는 함수
    public List<Long> getAllChildGroupIds() {
        return getChildGroups().stream()
                .flatMap(child -> Stream.concat(
                        Stream.of(
                                child.getId()),
                        child.getAllChildGroupIds().stream())
                )
                .collect(Collectors.toList());
    }

}
