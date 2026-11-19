package com.finscope.domain.investmentobservation;

import com.finscope.domain.fundamentals.CompanyBusinessProfile;

public record ObservationCompany(Long instrumentId, String code, String name, CompanyBusinessProfile profile) {
}
