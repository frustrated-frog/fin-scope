package com.finscope.web.request;

import com.finscope.domain.fundamentals.CompanyBusinessNotes;
import lombok.Data;
import javax.validation.constraints.*;

@Data
public class SaveCompanyBusinessNotesRequest {
    @NotNull @Size(max = 2000)
    private String revenueModel = "";
    @NotNull @Size(max = 2000)
    private String customers = "";
    @NotNull @Size(max = 2000)
    private String costDrivers = "";
    @NotNull @Size(max = 2000)
    private String advantage = "";
    @NotNull @Size(max = 1000) @Pattern(regexp = "^$|https?://[^\\s]+")
    private String sourceUrl = "";
    @NotNull @Min(0)
    private Integer revision;

    public CompanyBusinessNotes toNotes() {
        var notes = new CompanyBusinessNotes();
        notes.setRevenueModel(revenueModel.strip());
        notes.setCustomers(customers.strip());
        notes.setCostDrivers(costDrivers.strip());
        notes.setAdvantage(advantage.strip());
        notes.setSourceUrl(sourceUrl.strip());
        notes.setRevision(revision);
        return notes;
    }
}
