package org.broadinstitute.hellbender.tools.funcotator.dataSources;

import htsjdk.samtools.util.Locatable;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;
import org.broadinstitute.hellbender.tools.funcotator.Funcotation;
import org.broadinstitute.hellbender.tools.funcotator.metadata.FuncotationMetadata;
import org.broadinstitute.hellbender.tools.funcotator.metadata.VcfFuncotationMetadata;

import java.util.Arrays;

/**
 * Implements fields for use in known locatables.
 *
 * This class should only be used in specialized cases.
 */
public class LocatableFuncotationCreator {

    final private static String CONTIG_FIELD_NAME = "CONTIG";
    final private static String START_FIELD_NAME = "START";
    final private static String END_FIELD_NAME = "END";

    final private static FuncotationMetadata METADATA =
            VcfFuncotationMetadata.create(
                    Arrays.asList(
                            new VCFInfoHeaderLine(CONTIG_FIELD_NAME,1, VCFHeaderLineType.String, "contig"),
                            new VCFInfoHeaderLine(START_FIELD_NAME,1, VCFHeaderLineType.Integer, "start position"),
                            new VCFInfoHeaderLine(END_FIELD_NAME,1, VCFHeaderLineType.Integer, "end position")

                    )
            );

    // TODO: Test
    // TODO: Docs (only creates the locatable fields)
    // TODO: Document that if you are trying to create a funcotation based on a variant context, use FuncotatorUtils.createFuncotations(...)
    public static Funcotation create(final Locatable locatable, final Allele altAllele, final String dataSourceName) {

        return TableFuncotation.create(Arrays.asList(CONTIG_FIELD_NAME, START_FIELD_NAME, END_FIELD_NAME),
                Arrays.asList(locatable.getContig(), String.valueOf(locatable.getStart()), String.valueOf(locatable.getEnd())),
                altAllele, dataSourceName, METADATA);
    }
}
