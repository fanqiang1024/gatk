package org.broadinstitute.hellbender.tools.funcotator;

import com.google.common.collect.ImmutableSortedMap;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.vcf.VCFConstants;
import org.broadinstitute.hellbender.GATKBaseTest;
import org.broadinstitute.hellbender.engine.ReferenceContext;
import org.broadinstitute.hellbender.engine.ReferenceDataSource;
import org.broadinstitute.hellbender.tools.copynumber.utils.annotatedinterval.AnnotatedInterval;
import org.broadinstitute.hellbender.utils.SimpleInterval;
import org.broadinstitute.hellbender.utils.io.IOUtils;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.SortedMap;

public class AnnotatedIntervalToSegmentVariantContextConverterUnitTest extends GATKBaseTest {

    @DataProvider
    public Object[][] provideSegmentConversion() {
        final SimpleInterval interval = new SimpleInterval("2", 999999, 1500000);
        final SortedMap<String, String> testAnnotationsAmp = ImmutableSortedMap.of("FIELD1", "VALUE1", "FIELD2", "VALUE2", "CALL", "+");
        return new Object[][] {
                {
                    new AnnotatedInterval(interval,
                            testAnnotationsAmp),
                    new VariantContextBuilder()
                        .chr(interval.getContig())
                        .start(interval.getStart())
                        .stop(interval.getEnd())
                        .attributes(testAnnotationsAmp).attribute(VCFConstants.END_KEY, String.valueOf(interval.getEnd()))
                        .alleles("G", "<INS>")
                        .make()

                }
        };
    }

    @Test(dataProvider = "provideSegmentConversion")
    public void testConvert(final AnnotatedInterval segment, final VariantContext gtVariantContext) {
        final ReferenceContext referenceContext = new ReferenceContext(ReferenceDataSource.of(IOUtils.getPath(b37Reference)),
                segment.getInterval());
        final VariantContext guess = AnnotatedIntervalToSegmentVariantContextConverter.convert(segment, referenceContext);

        // Check locatable
        Assert.assertEquals(new SimpleInterval(guess.getContig(), guess.getStart(), guess.getEnd()),
                new SimpleInterval(gtVariantContext.getContig(), gtVariantContext.getStart(), gtVariantContext.getEnd()));
        Assert.assertEquals(new SimpleInterval(guess.getContig(), guess.getStart(), guess.getEnd()),
                new SimpleInterval(segment.getContig(), segment.getStart(), segment.getEnd()), "Guess variant context " +
                        "did not match the original segment.");

        // Check alleles and attributes
        Assert.assertEquals(guess.getAlleles(), gtVariantContext.getAlleles());
        Assert.assertEquals(guess.getAttributes(), gtVariantContext.getAttributes());
    }
}
