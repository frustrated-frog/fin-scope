package com.finscope.rpc.research.material;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.rpc.acquisition.AcquisitionRequest;
import com.finscope.rpc.acquisition.AcquisitionResponse;
import com.finscope.rpc.acquisition.AcquisitionRuntime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.finscope.common.enums.research.AnnouncementContentIssue;
import com.finscope.domain.fundamentals.CompanyBusinessProfile;
import com.finscope.domain.research.ResearchSourceDocument;
import com.finscope.domain.research.material.ResearchMaterial;
import com.finscope.rpc.research.ResearchSourceReader;
import com.finscope.rpc.marketintel.ProviderContractException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class EastmoneyAnnouncementClientTest {
    @Test
    void usesActualPublicationTimeEvenWhenNoticeDateIsTomorrowAndKeepsPagination() {
        var requests = new java.util.ArrayList<AcquisitionRequest>();
        var client = client(request -> {
            requests.add(request);
            return response(request, """
                    {"success":1,"data":{"total_hits":101,"list":[
                    {"art_code":"AN202611131831691800","title":"杭电股份:关于回购股份事项前十名股东持股情况的公告",
                    "codes":[{"stock_code":"603618"}],"notice_date":"2026-11-14 00:00:00","display_time":"2026-11-13 16:21:58:346"},
                    {"art_code":"AN202611121831660465","title":"杭电股份:关于取得股票回购专项贷款承诺函的公告",
                    "codes":[{"stock_code":"603618"}],"notice_date":"2026-11-13 00:00:00","display_time":"2026-11-12 17:33:44:319"},
                    {"art_code":"AN0001","title":"无公开时间","codes":[{"stock_code":"603618"}],"notice_date":"2026-11-13"}
                    ]}}
                    """);
        });
        var result = client.page(LocalDateTime.parse("2026-11-11T20:00:00"), LocalDateTime.parse("2026-11-13T20:00:00"), 1, new CompanyAnnouncementEnrichmentContext());
        assertTrue(result.isHasMore());
        assertEquals(2, result.getMaterials().size());
        assertEquals(LocalDateTime.parse("2026-11-13T16:21:58"), result.getMaterials().get(0).getPublishedAt());
        assertTrue(result.getMaterials().get(0).getContent().contains("2026-11-14"));
        assertTrue(requests.get(0).getUri().getQuery().contains("end_time=2026-11-14"));
    }

    @Test
    void validatesContentIdentityAndDoesNotOverwritePublicationTime() {
        var client = client(request -> response(request, "{}"));
        com.finscope.rpc.research.ResearchSourceReader reader = url -> new com.finscope.domain.research.ResearchSourceDocument(
                url, "公司公告", "证券代码：603618。公司于2026年11月11日召开董事会，审议通过回购预案。",
                "PDF", "pdf:pdfbox", "FETCHED");
        ReflectionTestUtils.setField(client, "sourceReader", reader);
        var value = new com.finscope.domain.research.material.ResearchMaterial();
        value.setExternalId("AN123");
        value.setStockCode("603618");
        value.setContent("公告日期：2026-11-14");
        value.setPublishedAt(LocalDateTime.parse("2026-11-13T16:21:58"));
        value.setProviderCode("EASTMONEY_ANNOUNCEMENT");
        value.setUrl("https://data.eastmoney.com/notices/detail/603618/AN123.html");
        var context = new CompanyAnnouncementEnrichmentContext();
        context.register(value, "SH");
        client.enrich(value, context);
        assertTrue(value.getContent().contains("公告原文："));
        assertEquals(LocalDateTime.parse("2026-11-13T16:21:58"), value.getPublishedAt());
        value.setStockCode("600000");
        assertThrows(com.finscope.rpc.marketintel.ProviderContractException.class, () -> client.enrich(value, context));
    }

    @Test
    void acceptsCommitteeAndLegalAttachmentsWithoutStockCodeUsingFullIssuerName() {
        var fixture = fixture("山西仟源医药集团股份有限公司\n薪酬与考核委员会关于回购注销限制性股票的核查意见");
        fixture.client().enrich(fixture.materials().get(0), fixture.context());
        when(fixture.reader().read(anyString())).thenAnswer(invocation -> new ResearchSourceDocument(invocation.getArgument(0), "法律意见书",
                "国浩律师（上海）事务所\n关于山 西 仟 源 医 药 集 团 股 份 有 限 公 司\n回购注销限制性股票事项之法律意见书", "PDF", "pdf:pdfbox", "FETCHED"));
        fixture.client().enrich(fixture.materials().get(1), fixture.context());
        assertTrue(fixture.materials().get(0).getContent().contains("公告原文："));
        assertTrue(fixture.materials().get(1).getContent().contains("法律意见书"));
        verify(fixture.profiles(), times(1)).fetch(argThat(value -> "300254".equals(value.getCode()) && "SZ".equals(value.getMarket())));
        assertEquals(LocalDateTime.parse("2026-11-09T18:00:00"), fixture.materials().get(0).getPublishedAt());
        // 同步轮次结束即释放名称缓存；新一轮重新核验名称，避免公司更名后继续沿用旧身份。
        var next = new CompanyAnnouncementEnrichmentContext();
        var material = fixture.client().page(LocalDateTime.parse("2026-11-09T00:00:00"), LocalDateTime.parse("2026-11-09T23:59:59"), 1, next).getMaterials().get(0);
        fixture.client().enrich(material, next);
        verify(fixture.profiles(), times(2)).fetch(any());
    }

    @Test
    void fullWidthSecurityCodeWorksWithoutAnotherCompanyProfileRequest() {
        var fixture = fixture("证券代码：３００２５４\n仟源医药：回购注销股份事项");
        fixture.client().enrich(fixture.materials().get(0), fixture.context());
        verifyNoInteractions(fixture.profiles());
        var adjacentDate = fixture("证券代码：300254 2026年11月9日\n回购注销股份事项");
        adjacentDate.client().enrich(adjacentDate.materials().get(0), adjacentDate.context());
        verifyNoInteractions(adjacentDate.profiles());
    }

    @Test
    void explicitlyWrongSecurityCodeCannotBeOverriddenByMatchingCompanyName() {
        var fixture = fixture("证券代码：600000\n山西仟源医药集团股份有限公司作为交易对手参与相关事项。");
        assertRejected(fixture, AnnouncementContentIssue.ISSUER_MISMATCH);
        verifyNoInteractions(fixture.profiles());
        assertRejected(fixture("证券代码：3002548\n山西仟源医药集团股份有限公司"), AnnouncementContentIssue.ISSUER_MISMATCH);
    }

    @Test
    void shortNamesIncidentalNumbersAndCompanyNamesOnlyAtTheEndAreInsufficient() {
        for (String body : List.of("仟源医药：关于回购注销股份事项的公告", "合同编号30025402，其他公司回购事项。",
                "某其他公司事项。".repeat(150) + "山西仟源医药集团股份有限公司为相关交易对手。")) {
            assertRejected(fixture(body), AnnouncementContentIssue.ISSUER_UNCONFIRMED);
        }
        var incompleteProfile = fixture("仟源医药：回购注销股份事项");
        var shortName = new CompanyBusinessProfile();
        shortName.setCompanyName("仟源医药");
        when(incompleteProfile.profiles().fetch(any())).thenReturn(shortName);
        assertRejected(incompleteProfile, AnnouncementContentIssue.ISSUER_UNCONFIRMED);
    }

    @Test
    void refusesMaterialNotBoundToTheCurrentListBeforeDownloadingPdf() {
        var fixture = fixture("证券代码：300254\n仟源医药回购事项");
        fixture.materials().get(0).setExternalId("AN999");
        assertRejected(fixture, AnnouncementContentIssue.LISTING_MISMATCH);
        verifyNoInteractions(fixture.reader(), fixture.profiles());
        fixture.materials().get(0).setExternalId("AN202611091831557499");
        fixture.materials().get(0).setUrl("https://example.com/wrong.pdf");
        assertRejected(fixture, AnnouncementContentIssue.LISTING_MISMATCH);
    }

    @Test
    void distinguishesEmptyBodyWrongDocumentReadFailureAndIssuerLookupFailure() {
        var fixture = fixture("   ");
        assertRejected(fixture, AnnouncementContentIssue.EMPTY_BODY);
        when(fixture.reader().read(anyString())).thenReturn(document("WEB_PAGE", "证券代码：300254"));
        assertRejected(fixture, AnnouncementContentIssue.UNEXPECTED_DOCUMENT);
        when(fixture.reader().read(anyString())).thenReturn(new ResearchSourceDocument("https://pdf.dfcfw.com/pdf/H2_AN999_1.pdf", "错误附件",
                "证券代码：300254", "PDF", "pdf:pdfbox", "FETCHED"));
        assertRejected(fixture, AnnouncementContentIssue.UNEXPECTED_DOCUMENT);
        when(fixture.reader().read(anyString())).thenThrow(new IllegalStateException("PDF extraction failed"));
        assertRejected(fixture, AnnouncementContentIssue.BODY_READ_FAILED);
        var unavailable = fixture("山西仟源医药集团股份有限公司\n法律意见书");
        when(unavailable.profiles().fetch(any())).thenThrow(new IllegalStateException("offline"));
        assertRejected(unavailable, AnnouncementContentIssue.ISSUER_LOOKUP_FAILED);
    }

    private void assertRejected(Fixture fixture, AnnouncementContentIssue issue) {
        var material = fixture.materials().get(0);
        String original = material.getContent();
        var error = assertThrows(ProviderContractException.class, () -> fixture.client().enrich(material, fixture.context()));
        assertEquals(issue.name(), error.getErrorType());
        assertEquals(original, material.getContent());
    }

    private Fixture fixture(String body) {
        var client = client(request -> response(request, """
                {"success":1,"data":{"total_hits":2,"list":[
                {"art_code":"AN202611091831557499","title":"仟源医药：回购注销核查意见","codes":[{"stock_code":"300254","market_code":"0"}],"display_time":"2026-11-09 18:00:00"},
                {"art_code":"AN202611091831557506","title":"仟源医药：回购注销法律意见书","codes":[{"stock_code":"300254","market_code":"0"}],"display_time":"2026-11-09 18:01:00"}
                ]}}
                """));
        var reader = mock(ResearchSourceReader.class);
        when(reader.read(anyString())).thenAnswer(invocation -> new ResearchSourceDocument(invocation.getArgument(0), "公告", body,
                "PDF", "pdf:pdfbox", "FETCHED"));
        var profiles = mock(CompanyBusinessProfileClient.class);
        var profile = new CompanyBusinessProfile();
        profile.setCompanyName("山西仟源医药集团股份有限公司");
        when(profiles.fetch(any())).thenReturn(profile);
        ReflectionTestUtils.setField(client, "sourceReader", reader);
        ReflectionTestUtils.setField(client, "profiles", profiles);
        var context = new CompanyAnnouncementEnrichmentContext();
        var materials = client.page(LocalDateTime.parse("2026-11-09T00:00:00"), LocalDateTime.parse("2026-11-09T23:59:59"), 1, context).getMaterials();
        return new Fixture(client, context, materials, reader, profiles);
    }

    private ResearchSourceDocument document(String type, String body) {
        return new ResearchSourceDocument("https://pdf.dfcfw.com/pdf/H2_AN202611091831557499_1.pdf", "公告", body, type, "pdf:pdfbox", "FETCHED");
    }

    private record Fixture(EastmoneyAnnouncementClient client, CompanyAnnouncementEnrichmentContext context,
                           List<ResearchMaterial> materials, ResearchSourceReader reader, CompanyBusinessProfileClient profiles) {
    }

    private EastmoneyAnnouncementClient client(AcquisitionRuntime runtime) {
        var value = new EastmoneyAnnouncementClient();
        ReflectionTestUtils.setField(value, "runtime", runtime);
        ReflectionTestUtils.setField(value, "json", new ObjectMapper());
        return value;
    }

    private AcquisitionResponse response(AcquisitionRequest request, String body) {
        return new AcquisitionResponse(request.getUri(), request.getUri(), 200, Map.of(),
                body.getBytes(StandardCharsets.UTF_8), body, "application/json", "UTF-8", "hash", 1, 1, Instant.now());
    }
}
