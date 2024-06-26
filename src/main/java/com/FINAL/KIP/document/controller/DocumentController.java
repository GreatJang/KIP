package com.FINAL.KIP.document.controller;


import com.FINAL.KIP.document.dto.req.CreateDocumentReqDto;
import com.FINAL.KIP.document.dto.req.moveDocInGroupReqDto;
import com.FINAL.KIP.document.dto.req.updateDocGroupIdReqDto;
import com.FINAL.KIP.document.dto.req.updateDocTitleReqDto;
import com.FINAL.KIP.document.dto.res.DocumentResDto;
import com.FINAL.KIP.document.dto.res.DocumentVersionResDto;
import com.FINAL.KIP.document.dto.res.JustDocTitleResDto;
import com.FINAL.KIP.document.service.DocumentService;
import com.FINAL.KIP.version.dto.response.VersionDetailResDto;
import com.FINAL.KIP.version.dto.response.VersionReplaceResDto;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/doc")
public class DocumentController {

    private final DocumentService documentService;

    @Autowired
    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    //    Create
    @PostMapping
    public ResponseEntity<DocumentResDto> createDocument(@RequestBody CreateDocumentReqDto dto) {
        return ResponseEntity.ok(documentService.createDocument(dto));
    }

    //    Read
    @GetMapping("public")
    public ResponseEntity<List<JustDocTitleResDto>> getPublicDocuments(){
        return ResponseEntity.ok(documentService.getPublicDocuments());
    }

    @GetMapping("{documentId}")
    public ResponseEntity<DocumentResDto> getDocumentById(@PathVariable Long documentId) {
        return ResponseEntity.ok(documentService.getIsAccessibleDoc(documentId));
    }

    @GetMapping("{groupId}/linked")
    public ResponseEntity<List<JustDocTitleResDto>> getLinkedDocumentsByGroupId(@PathVariable Long groupId) {
        return ResponseEntity.ok(documentService.getLinkedDocumentsByGroupId(groupId));
    }


    //  Update
    @PatchMapping("title")
    public ResponseEntity<JustDocTitleResDto> updateDocumentTitle(
        @RequestBody updateDocTitleReqDto dto) {
        return ResponseEntity.ok(documentService.updateDocumentTitle(dto));
    }

    @PatchMapping("move") // 그룹내 문서 이동
    public ResponseEntity<List<JustDocTitleResDto>> moveDocumentInGroup(@RequestBody moveDocInGroupReqDto dto){
        return ResponseEntity.ok(documentService.moveDocumentInGroup(dto));
    }

    @PatchMapping("{DocumentId}/type") // 문서 섹션, 컨텐츠 변경
    public ResponseEntity<DocumentResDto> updateDocumentType(@PathVariable Long DocumentId) {
        return ResponseEntity.ok(documentService.updateDocumentType(DocumentId));
    }

    @PatchMapping("{DocumentId}/public") // 그룹문서 => 전체공개
    public ResponseEntity<DocumentResDto> updateDocumentPublic(@PathVariable Long DocumentId) {
        return ResponseEntity.ok(documentService.updateDocumentPublic(DocumentId));
    }

    @PatchMapping("group") // 전체공개 => 그룹문서
    public ResponseEntity<DocumentResDto> updatePublicDocumentGroupId(
        @RequestBody updateDocGroupIdReqDto dto) {
        return ResponseEntity.ok(documentService.updatePublicDocumentGroupId(dto));
    }


    //    Delete
    @DeleteMapping("{DocumentId}")
    public ResponseEntity<Void> deletedDocument(@PathVariable Long DocumentId) {
        documentService.deleteDocument(DocumentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("{documentId}/version")
    public ResponseEntity<List<DocumentVersionResDto>> getAllVersion(
        @PathVariable Long documentId) {
        return documentService.getAllVersion(documentId);
    }

    @PatchMapping("/{versionId}")
    public ResponseEntity<VersionReplaceResDto> replaceVersion(
        @PathVariable Long versionId) {
        return documentService.replaceVersion(versionId);
    }

    @PostMapping("/image")
    @CrossOrigin
    public String uploadEditorImage(@RequestParam MultipartFile image) {
		try {
			return documentService.imageSave(image);
		} catch (IOException e) {
            return "Fail to upload image!";
		}
	}

}
