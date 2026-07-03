package com.b2b.instantneed.admin.controller;

import com.b2b.instantneed.admin.service.AdminThumbnailBackfillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin — Media", description = "Catalog image maintenance (ROLE_ADMIN)")
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/v1/admin/media")
@RequiredArgsConstructor
public class AdminMediaController {

    private final AdminThumbnailBackfillService backfillService;

    @Operation(summary = "Generate thumbnails for existing categories/product images that predate thumbnail support. Idempotent — safe to re-run.")
    @PostMapping("/backfill-thumbnails")
    public ResponseEntity<AdminThumbnailBackfillService.BackfillResult> backfillThumbnails() {
        return ResponseEntity.ok(backfillService.backfill());
    }
}
