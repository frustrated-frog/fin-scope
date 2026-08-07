package com.finscope.rpc.company;

import com.finscope.domain.company.CompanySearchResult;

import java.util.List;

public interface CompanyDirectoryProvider {
    String providerCode();
    List<CompanySearchResult> search(String query, int limit);
}
