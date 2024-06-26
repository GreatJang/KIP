package com.FINAL.KIP.group.controller;


import com.FINAL.KIP.group.dto.req.CreateGroupReqDto;
import com.FINAL.KIP.group.dto.req.UpdateGroupReqDto;
import com.FINAL.KIP.group.dto.req.addUsersToGroupReqDto;
import com.FINAL.KIP.group.dto.res.GetGroupHierarchyResDto;
import com.FINAL.KIP.group.dto.res.GroupResDto;
import com.FINAL.KIP.group.dto.res.GroupUsersResDto;
import com.FINAL.KIP.group.dto.res.GroupUsersRoleResDto;
import com.FINAL.KIP.group.service.GroupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("group")
public class GroupController {

    private final GroupService groupService;

    @Autowired
    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }


    // Create
    @PostMapping
    public ResponseEntity<GetGroupHierarchyResDto> createGroup(
            @RequestBody CreateGroupReqDto dto) {
        return ResponseEntity.ok(
                groupService.createGroupAndReturnHierrachy(dto)
        );
    }

    @PostMapping("list")
    public ResponseEntity<List<GroupResDto>> createGroups(
            @RequestBody List<CreateGroupReqDto> dtos) {
        return ResponseEntity.ok(
                groupService.createGroups(dtos));
    }


    // Read
    @GetMapping("{groupId}")
    public ResponseEntity<GroupResDto> getGroupInfoById(
            @PathVariable Long groupId) {
        return ResponseEntity.ok(
                groupService.getGroupInfoById(groupId)
        );
    }

    @GetMapping("list")
    public ResponseEntity<List<GroupResDto>> getGroups() {
        return ResponseEntity.ok(
                groupService.getGroups()
        );
    }

    @GetMapping("childgroups/{groupId}")
    public ResponseEntity<List<GroupResDto>> getGroupChilds(
            @PathVariable Long groupId) {
        return ResponseEntity.ok(
                groupService.getGroupsById(groupId)
        );
    }

    @GetMapping("hierarchy/{groupId}")
    public ResponseEntity<GetGroupHierarchyResDto> getGroupHierarchy(
            @PathVariable Long groupId) {
        return ResponseEntity.ok(
                groupService.getGroupHierarchy(groupId)
        );
    }

    //  Update
    @PatchMapping
    public ResponseEntity<GetGroupHierarchyResDto> updateGroupInfo (
            @RequestBody UpdateGroupReqDto dto){
        groupService.updateGroupInfo(dto);
        // 업데이트 이후 그룹의 하이라키 정보를 리턴
        return ResponseEntity.ok(groupService.getGroupHierarchy(1L));
    }


    //  Delete
    @DeleteMapping("{groupId}")
    public ResponseEntity<GetGroupHierarchyResDto> deleteGroup (
            @PathVariable Long groupId) {
        return ResponseEntity.ok(groupService.deleteGroup(groupId));
    }


                            /* GroupUser */

    //  Create
    @PostMapping("addusers")
    public ResponseEntity<List<GroupUsersRoleResDto>> addUsersToGroup(
            @RequestBody addUsersToGroupReqDto dto) {
        return ResponseEntity.ok(
                groupService.addUsersToGroup(dto)
        );
    }

    @PostMapping("addusers/list")
    public ResponseEntity<List<List<GroupUsersRoleResDto>>> addUsersToGroup(
            @RequestBody List<addUsersToGroupReqDto> dtos) {
        return ResponseEntity.ok(
                groupService.addUsersToGroupList(dtos)
        );
    }


    //  Read
    @GetMapping("{groupId}/users")
    public ResponseEntity<GroupUsersResDto> getGroupUsers(
            @PathVariable Long groupId) {
        return ResponseEntity.ok(
                groupService.getGroupUsers(groupId)
        );
    }

    @GetMapping("mygroups")
    public ResponseEntity<List<GroupResDto>> getMyGroups(){
        return ResponseEntity.ok(
                groupService.getMyGroups()
        );
    }


    // Update
    @PatchMapping("{groupId}/{userId}/role")
    public ResponseEntity<GroupUsersRoleResDto> updateUserRoleInGroup(
            @PathVariable Long groupId, @PathVariable Long userId) {
        return ResponseEntity.ok(groupService.updateUserRoleInGroup(groupId, userId));
    }

    //  Delete
    @DeleteMapping("{groupId}/{userId}/delete")
    public ResponseEntity<GroupUsersResDto> removeUserFromGroup(
            @PathVariable Long groupId, @PathVariable Long userId) {
        return ResponseEntity.ok(groupService.removeUserFromGroup(groupId, userId));
    }
}

