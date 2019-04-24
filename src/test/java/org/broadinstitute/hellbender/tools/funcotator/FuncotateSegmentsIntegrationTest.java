package org.broadinstitute.hellbender.tools.funcotator;

import org.broadinstitute.hellbender.CommandLineProgramTest;
import org.broadinstitute.hellbender.cmdline.StandardArgumentDefinitions;
import org.broadinstitute.hellbender.testutils.ArgumentsBuilder;
import org.broadinstitute.hellbender.tools.copynumber.arguments.CopyNumberStandardArgument;
import org.broadinstitute.hellbender.tools.copynumber.utils.annotatedinterval.AnnotatedIntervalCollection;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class FuncotateSegmentsIntegrationTest extends CommandLineProgramTest {
    private static final String TEST_SUB_DIR = toolsTestDir + "/funcotator/";
    private static final String SIMPLE_TEST_FILE = TEST_SUB_DIR + "simple.seg";
    private static final String SIMPLE_TEST_CNTN4_FILE = TEST_SUB_DIR + "simple_cntn4_overlap.seg";
    private static final String REF = b37Reference;
    private static final String DS_PIK3CA_DIR  = largeFileTestDir + "funcotator" + File.separator + "small_ds_pik3ca" + File.separator;
    // This has transcripts with multiple gene names...
    private static final String DS_CNTN4_DIR  = toolsTestDir + "funcotator" + File.separator + "small_cntn4_ds" + File.separator;

    @Test
    public void testSimpleNoOverlap() throws IOException {
        final File outputFile = File.createTempFile("funcotatesegs_simple", ".seg");

        final ArgumentsBuilder arguments = new ArgumentsBuilder();
        arguments.add("--" + CopyNumberStandardArgument.SEGMENTS_FILE_LONG_NAME);
        arguments.add(SIMPLE_TEST_FILE);
        arguments.add("--" + FuncotatorArgumentDefinitions.OUTPUT_FORMAT_LONG_NAME);
        arguments.add(FuncotatorArgumentDefinitions.OutputFormatType.SEG);
        arguments.add("--" + StandardArgumentDefinitions.REFERENCE_LONG_NAME);
        arguments.add(REF);
        arguments.add("-" + StandardArgumentDefinitions.OUTPUT_SHORT_NAME);
        arguments.add(outputFile.getAbsolutePath());
        arguments.add("--" + FuncotatorArgumentDefinitions.REFERENCE_VERSION_LONG_NAME);
        arguments.add("hg19");
        arguments.addArgument(FuncotatorArgumentDefinitions.DATA_SOURCES_PATH_LONG_NAME, DS_PIK3CA_DIR);

        runCommandLine(arguments);

        final AnnotatedIntervalCollection collection = AnnotatedIntervalCollection.create(outputFile.toPath(), null);
        Assert.assertEquals(collection.getRecords().size(), 3);
        Assert.assertTrue(collection.getRecords().stream().allMatch(r -> r.hasAnnotation("genes")));
        Assert.assertTrue(collection.getRecords().stream().allMatch(r -> r.getAnnotationValue("genes").equals("")));
        Assert.assertTrue(collection.getRecords().stream().allMatch(r -> r.getAnnotationValue("start_gene").equals("")));
        Assert.assertTrue(collection.getRecords().stream().allMatch(r -> r.getAnnotationValue("end_gene").equals("")));
        Assert.assertTrue(collection.getRecords().stream().allMatch(r -> r.getAnnotationValue("start_exon").equals("")));
        Assert.assertTrue(collection.getRecords().stream().allMatch(r -> r.getAnnotationValue("end_exon").equals("")));
        Assert.assertTrue(collection.getRecords().stream().allMatch(r -> r.getAnnotationValue("ref_allele").equals("")));
        Assert.assertTrue(collection.getRecords().stream().allMatch(r -> r.getAnnotationValue("alt_allele").equals("")));
    }

    /**
     * Very dependent on the data in "simple_cntn4_overlap.seg"
     */
    @DataProvider
    public Object[][] cntn4GroundTruth() {
        return new Object[][] {
                {

                            // genes
                            Arrays.asList("CNTN4,CNTN4-AS2", "CNTN4,CNTN4-AS1", ""),
                            //start_gene
                            Arrays.asList("", "CNTN4", ""),
                            //end_gene
                            Arrays.asList("CNTN4", "", ""),
                            // ref_allele (always blank)
                            Arrays.asList("", "", ""),
                            // alt_allele (always blank)
                            Arrays.asList("", "", ""),
                            // Contig
                            Arrays.asList("3", "3", "3"),
                            // Start
                            Arrays.asList("2000000", "3000000", "3500001"),
                            // End
                            Arrays.asList("2500000", "3500000", "3900000"),
                            // Call
                            Arrays.asList("0", "-", "+"),
                            // Segment_Mean
                            Arrays.asList("0.037099", "0.001748", "0.501748"),
                            // Num_Probes
                            Arrays.asList("2000", "3000", "4000")

                }
        };
    }

    @Test(dataProvider = "cntn4GroundTruth")
    public void testSimpleMultipleGenesOverlap(List<String> gtGenesValues, List<String> gtStartGeneValues, List<String> gtEndGeneValues,
                                               List<String> gtRefAlleles, List<String> gtAltAlleles,
                                               List<String> gtContigs, List<String> gtStarts,
                                               List<String> gtEnds, List<String> gtCalls,
                                               List<String> gtSegmentMeans, List<String> gtNumProbes) throws IOException {
        final File outputFile = File.createTempFile("funcotatesegs_simple_cntn4", ".seg");

        final ArgumentsBuilder arguments = new ArgumentsBuilder();
        arguments.add("--" + CopyNumberStandardArgument.SEGMENTS_FILE_LONG_NAME);
        arguments.add(SIMPLE_TEST_CNTN4_FILE);
        arguments.add("--" + FuncotatorArgumentDefinitions.OUTPUT_FORMAT_LONG_NAME);
        arguments.add(FuncotatorArgumentDefinitions.OutputFormatType.SEG);
        arguments.add("--" + StandardArgumentDefinitions.REFERENCE_LONG_NAME);
        arguments.add(REF);
        arguments.add("-" + StandardArgumentDefinitions.OUTPUT_SHORT_NAME);
        arguments.add(outputFile.getAbsolutePath());
        arguments.add("--" + FuncotatorArgumentDefinitions.REFERENCE_VERSION_LONG_NAME);
        arguments.add("hg19");
        arguments.addArgument(FuncotatorArgumentDefinitions.DATA_SOURCES_PATH_LONG_NAME, DS_CNTN4_DIR);

        runCommandLine(arguments);

        final AnnotatedIntervalCollection collection = AnnotatedIntervalCollection.create(outputFile.toPath(), null);
        Assert.assertEquals(collection.getRecords().size(), 3);

        final List<String> testGenesValues = collection.getRecords().stream().map(r -> r.getAnnotationValue("genes")).collect(Collectors.toList());
        Assert.assertEquals(testGenesValues, gtGenesValues);
        final List<String> testStartGeneValues = collection.getRecords().stream().map(r -> r.getAnnotationValue("start_gene")).collect(Collectors.toList());
        Assert.assertEquals(testStartGeneValues, gtStartGeneValues);
        final List<String> testEndGeneValues = collection.getRecords().stream().map(r -> r.getAnnotationValue("end_gene")).collect(Collectors.toList());
        Assert.assertEquals(testEndGeneValues, gtEndGeneValues);
        final List<String> testRefAlleleValues = collection.getRecords().stream().map(r -> r.getAnnotationValue("ref_allele")).collect(Collectors.toList());
        Assert.assertEquals(testRefAlleleValues, gtRefAlleles);
        final List<String> testAltAlleleValues = collection.getRecords().stream().map(r -> r.getAnnotationValue("alt_allele")).collect(Collectors.toList());
        Assert.assertEquals(testAltAlleleValues, gtAltAlleles);
//        final List<String> testAltAlleleValues = collection.getRecords().stream().map(r -> r.getAnnotationValue("chr")).collect(Collectors.toList());
//        Assert.assertEquals(testAltAlleleValues, gtAltAlleles);
//        final List<String> testAltAlleleValues = collection.getRecords().stream().map(r -> r.getAnnotationValue("alt_allele")).collect(Collectors.toList());
//        Assert.assertEquals(testAltAlleleValues, gtAltAlleles);
//        final List<String> testAltAlleleValues = collection.getRecords().stream().map(r -> r.getAnnotationValue("alt_allele")).collect(Collectors.toList());
//        Assert.assertEquals(testAltAlleleValues, gtAltAlleles);
//        final List<String> testAltAlleleValues = collection.getRecords().stream().map(r -> r.getAnnotationValue("alt_allele")).collect(Collectors.toList());
//        Assert.assertEquals(testAltAlleleValues, gtAltAlleles);
//        final List<String> testAltAlleleValues = collection.getRecords().stream().map(r -> r.getAnnotationValue("alt_allele")).collect(Collectors.toList());
//        Assert.assertEquals(testAltAlleleValues, gtAltAlleles);
//        final List<String> testAltAlleleValues = collection.getRecords().stream().map(r -> r.getAnnotationValue("alt_allele")).collect(Collectors.toList());
//        Assert.assertEquals(testAltAlleleValues, gtAltAlleles);
        // TODO: Check the other fields (sample	Chromosome	Start	End	Segment_Call	Num_Probes	Segment_Mean)
    }

    // TODO: hg38 test
}
