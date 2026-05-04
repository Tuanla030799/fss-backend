package com.fss.backend.masterdata;

import com.fss.backend.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MasterDataController {
    private final MasterDataService service;

    public MasterDataController(MasterDataService service) {
        this.service = service;
    }

    @GetMapping("/master-data")
    public ApiResponse<MasterDataResponse> publicMasterData() {
        return ApiResponse.ok("OK", service.publicMasterData());
    }

    @GetMapping("/admin/master-data")
    public ApiResponse<MasterDataResponse> adminMasterData() {
        return ApiResponse.ok("OK", service.adminMasterData());
    }
}
