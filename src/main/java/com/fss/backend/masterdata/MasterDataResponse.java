package com.fss.backend.masterdata;

import java.util.List;

public record MasterDataResponse(List<MasterDataOption> categories,
                                 List<MasterDataOption> brands,
                                 List<MasterDataOption> collections,
                                 List<StaticOption> productGenders,
                                 List<StaticOption> productStatuses,
                                 List<StaticOption> commonStatuses) {}
