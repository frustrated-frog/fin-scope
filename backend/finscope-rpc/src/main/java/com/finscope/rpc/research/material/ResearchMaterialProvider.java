package com.finscope.rpc.research.material;

import com.finscope.domain.research.material.ResearchMaterial;
import com.finscope.domain.research.material.ResearchMaterialType;
import com.finscope.rpc.marketdata.ProviderResult;
import com.finscope.rpc.provider.ExternalDataProvider;

import java.util.List;
import java.util.Set;

public interface ResearchMaterialProvider extends ExternalDataProvider {
    Set<ResearchMaterialType> materialTypes();

    default boolean supports(ResearchMaterialType type, ResearchMaterialRequest request) {
        return type != null && request != null && materialTypes().contains(type);
    }

    ProviderResult<List<ResearchMaterial>> fetch(ResearchMaterialType type, ResearchMaterialRequest request);
}
