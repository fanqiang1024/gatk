package org.broadinstitute.hellbender.tools.funcotator.simpletsvoutput;

import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;
import org.broadinstitute.hellbender.GATKBaseTest;
import org.broadinstitute.hellbender.testutils.FuncotatorReferenceTestUtils;
import org.broadinstitute.hellbender.tools.funcotator.AnnotatedIntervalToSegmentVariantContextConverter;
import org.broadinstitute.hellbender.tools.funcotator.FuncotationMap;
import org.broadinstitute.hellbender.tools.funcotator.dataSources.TableFuncotation;
import org.broadinstitute.hellbender.tools.funcotator.metadata.FuncotationMetadata;
import org.broadinstitute.hellbender.tools.funcotator.metadata.VcfFuncotationMetadata;
import org.broadinstitute.hellbender.utils.io.Resource;
import org.broadinstitute.hellbender.utils.test.FuncotatorTestUtils;
import org.broadinstitute.hellbender.utils.tsv.TableReader;
import org.broadinstitute.hellbender.utils.tsv.TableUtils;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SimpleTsvOutputRendererUnitTest extends GATKBaseTest {
    private static final String TEST_SUB_DIR = toolsTestDir + "/funcotator/";
    private static final String SEG_CONFIG_FILE = TEST_SUB_DIR + "test_tsv_output.config";
    private static final String SEG_RESOURCE_FILE = "org/broadinstitute/hellbender/tools/funcotator/simple_funcotator_seg_file.config";

    @DataProvider
    public Object[][] provideForSimpleSegFileWriting() {
        return new Object[][] {
                {FuncotatorTestUtils.createSimpleVariantContext(FuncotatorReferenceTestUtils.retrieveHg19Chr3Ref(),
                        "3", 2100000, 3200000, "T",
                        AnnotatedIntervalToSegmentVariantContextConverter.COPY_NEUTRAL_ALLELE.getDisplayString())
                }
        };
    }

    @Test(dataProvider = "provideForSimpleSegFileWriting")
    public void testSimpleSegFileWriting(final VariantContext segVC) throws IOException {
        final File outputFile = File.createTempFile("simpleSegFileWriting", ".seg");

        final SimpleTsvOutputRenderer renderer = SimpleTsvOutputRenderer.createFromFile(outputFile.toPath(),
                new LinkedHashMap<>(),
                new LinkedHashMap<>(), new HashSet<>(), Paths.get(SEG_CONFIG_FILE), "TEST");

        final List<String> fieldNames = Arrays.asList("Gencode_19_genes", "foo1", "foobar2", "TEST3", "foo3!!");
        final List<String> fieldValues = Arrays.asList("GENE1,GENE2", "stuff1", "stuff2", "stuff3", "stuff4");
        final FuncotationMap funcotationMap = FuncotationMap.createNoTranscriptInfo(
                Collections.singletonList(
                        TableFuncotation.create(fieldNames, fieldValues, segVC.getAlternateAllele(0),
                                "TEST", createDummySegmentFuncotationMetadata())

                ));
        renderer.write(segVC, funcotationMap);
        renderer.close();

        final TableReader<LinkedHashMap<String,String>> outputReader = TableUtils.reader(outputFile.toPath(),
                (columns, exceptionFactory) -> {
                    return (dataLine) -> {
                        final int columnCount = columns.names().size();
                        return IntStream.range(0, columnCount).boxed().collect(Collectors.toMap(i -> columns.names().get(i),
                                i -> dataLine.get(i),
                                (x1, x2) -> {
                                    throw new IllegalArgumentException("Should not be able to have duplicate field names.");
                                },
                                LinkedHashMap::new));

                    };
                }
        );

        // Create the entire list of OUTPUT field names in order.
        //   Note that the locatable fields are always emitted in alphabetical order if no aliases are found.
        final List<String> outputFieldNames = Arrays.asList("genes", "foo1", "foo2", "foo3", "foo3!!", "CONTIG", "END", "START");
        final List<String> outputFieldValues = new ArrayList<>();
        outputFieldValues.addAll(fieldValues);
        outputFieldValues.addAll(Arrays.asList(segVC.getContig(), String.valueOf(segVC.getEnd()), String.valueOf(segVC.getStart())));

        // Check that the ordering of the column is correct.
        final List<LinkedHashMap<String,String>> outputRecords = outputReader.toList();
        Assert.assertEquals(outputRecords.size(), 1);
        final LinkedHashMap<String, String> onlyRecord = outputRecords.get(0);
        Assert.assertEquals(new ArrayList<>(onlyRecord.keySet()), outputFieldNames);

        // Check the values.
        Assert.assertEquals(onlyRecord.keySet().stream().map(k -> onlyRecord.get(k)).collect(Collectors.toList()), outputFieldValues);
    }

    private static FuncotationMetadata createDummySegmentFuncotationMetadata() {
        return VcfFuncotationMetadata.create(
            Arrays.asList(
                    new VCFInfoHeaderLine("Gencode_19_genes",1, VCFHeaderLineType.String, "The genes overlapping the segment."),
                    new VCFInfoHeaderLine("foo1",1, VCFHeaderLineType.String, "foo1"),
                    new VCFInfoHeaderLine("foobar2",1, VCFHeaderLineType.String, "foobar2 (an alias relative to the config file)"),
                    new VCFInfoHeaderLine("TEST3",1, VCFHeaderLineType.String, "Note that this has no spaces"),
                    new VCFInfoHeaderLine("foo3!!",1, VCFHeaderLineType.String, "special character....")
            )
        );
    }

    @DataProvider()
    public Object[][] provideConfigFiles() throws IOException {

        // test_tsv_output.seg
        LinkedHashMap<String, List<String>> gt1 = new LinkedHashMap<>();
        gt1.put("genes", Collections.singletonList("Gencode_19_genes"));
        gt1.put("foo1", Collections.emptyList());
        gt1.put("foo2", Arrays.asList("TEST2", "foobar2"));
        gt1.put("foo3", Arrays.asList("TEST3", "foobar2"));
        gt1.put("foo3!!", Collections.emptyList());

        // Actual resource for funcotator seg files.
        LinkedHashMap<String, List<String>> gt2 = new LinkedHashMap<>();
        gt2.put("alt_allele", Arrays.asList(SimpleTsvOutputRenderer.splitAndTrim("Gencode_19_alt_allele,Gencode_27_alt_allele,Gencode_28_alt_allele", ",")));
        gt2.put("end_gene", Arrays.asList(SimpleTsvOutputRenderer.splitAndTrim("Gencode_19_end_gene,Gencode_27_end_gene,Gencode_28_end_gene", ",")));
        gt2.put("end", Arrays.asList(SimpleTsvOutputRenderer.splitAndTrim("END,End,End_Position,end_position,chromEnd,segment_end,End_position,target_end,stop,Stop,Position,position,pos,POS,segment_end", ",")));
        gt2.put("start_gene", Arrays.asList(SimpleTsvOutputRenderer.splitAndTrim("Gencode_19_start_gene,Gencode_27_start_gene,Gencode_28_start_gene", ",")));
        gt2.put("Segment_Mean", Arrays.asList(SimpleTsvOutputRenderer.splitAndTrim("MEAN_LOG2_COPY_RATIO", ",")));
        gt2.put("genes", Arrays.asList(SimpleTsvOutputRenderer.splitAndTrim("Gencode_19_genes,Gencode_27_genes,Gencode_28_genes", ",")));
        gt2.put("Sample", Arrays.asList(SimpleTsvOutputRenderer.splitAndTrim("sample,sample_id", ",")));
        gt2.put("start", Arrays.asList(SimpleTsvOutputRenderer.splitAndTrim("START,Start,Start_Position,start_position,chromStart,segment_start,Start_position,target_start,Position,position,pos,POS,segment_start", ",")));
        gt2.put("chr", Arrays.asList(SimpleTsvOutputRenderer.splitAndTrim("CONTIG,contig,Chromosome,chrom,chromosome,Chrom,seqname,seqnames,CHROM,target_contig,segment_contig", ",")));
        gt2.put("build", Collections.emptyList());
        gt2.put("Num_Probes", Arrays.asList(SimpleTsvOutputRenderer.splitAndTrim("NUM_POINTS_COPY_RATIO", ",")));
        gt2.put("start_exon", Arrays.asList(SimpleTsvOutputRenderer.splitAndTrim("Gencode_19_start_exon,Gencode_27_start_exon,Gencode_28_start_exon", ",")));
        gt2.put("end_exon", Arrays.asList(SimpleTsvOutputRenderer.splitAndTrim("Gencode_19_end_exon,Gencode_27_end_exon,Gencode_28_end_exon", ",")));
        gt2.put("ref_allele", Arrays.asList(SimpleTsvOutputRenderer.splitAndTrim("Gencode_19_ref_allele,Gencode_27_ref_allele,Gencode_28_ref_allele", ",")));
        gt2.put("Segment_Call", Collections.emptyList());

        return new Object[][] {
                {Paths.get(SEG_CONFIG_FILE), gt1},
                {Resource.getResourceContentsAsFile(SEG_RESOURCE_FILE).toPath(), gt2}
        };
    }

    @Test(dataProvider = "provideConfigFiles")
    public void testLoadingConfigFile(Path configFile, LinkedHashMap<String, List<String>> gt) {
        LinkedHashMap<String, List<String>> guess = SimpleTsvOutputRenderer.createColumnNameToAliasesMap(configFile);
        assertLinkedHashMapsEqual(guess, gt);
    }

    private <T,U> void assertLinkedHashMapsEqual(final LinkedHashMap<T, U> guess, final LinkedHashMap<T, U> gt) {
        // Since these are linked hash maps, the tests below should also de facto test that the ordering is correct.
        Assert.assertEquals(new ArrayList<>(guess.keySet()), new ArrayList<>(gt.keySet()));
        Assert.assertEquals(new ArrayList<>(guess.values()), new ArrayList<>(gt.values()));
    }

    // TODO: Update other test to just use a dictionary of aliases, not the config file.

    // TODO: Test override
    // TODO: Test defaults
}
