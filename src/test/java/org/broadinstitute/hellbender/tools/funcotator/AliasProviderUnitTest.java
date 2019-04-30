package org.broadinstitute.hellbender.tools.funcotator;

import org.broadinstitute.hellbender.GATKBaseTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

public class AliasProviderUnitTest extends GATKBaseTest {

    @DataProvider
    public Object[][] provideColumnNameToFieldName() {
        return new Object[][] {
                {
                    new LinkedHashMap<>(), FuncotationMap.createNoTranscriptInfo(Collections.emptyList()), FuncotationMap.NO_TRANSCRIPT_AVAILABLE_KEY,
                        new LinkedHashMap<>()
                },{
                    new LinkedHashMap<>(), FuncotationMap.createNoTranscriptInfo(Collections.emptyList()), FuncotationMap.NO_TRANSCRIPT_AVAILABLE_KEY,
                        new LinkedHashMap<>()
                },
        };
    }

    @Test(dataProvider = "provideColumnNameToFieldName")
    public void testColumnNameToFieldName(final LinkedHashMap<String, List<String>> aliasMap, final FuncotationMap funcotationMap, final String txId, final LinkedHashMap<String, String> gt) {
        final AliasProvider aliasProvider = new AliasProvider(aliasMap);
        final LinkedHashMap<String, String> guess =
                aliasProvider.createColumnNameToFieldNameMap(funcotationMap, txId);
        Assert.assertEquals(guess, gt);
    }
}
