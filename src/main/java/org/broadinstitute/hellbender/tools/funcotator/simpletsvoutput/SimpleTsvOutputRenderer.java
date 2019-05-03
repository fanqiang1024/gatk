package org.broadinstitute.hellbender.tools.funcotator.simpletsvoutput;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.VariantContext;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.broadinstitute.barclay.utils.Utils;
import org.broadinstitute.hellbender.exceptions.GATKException;
import org.broadinstitute.hellbender.exceptions.UserException;
import org.broadinstitute.hellbender.tools.funcotator.AliasProvider;
import org.broadinstitute.hellbender.tools.funcotator.FuncotationMap;
import org.broadinstitute.hellbender.tools.funcotator.OutputRenderer;
import org.broadinstitute.hellbender.tools.funcotator.dataSources.LocatableFuncotationCreator;
import org.broadinstitute.hellbender.tools.funcotator.mafOutput.MafOutputRenderer;
import org.broadinstitute.hellbender.utils.io.Resource;
import org.broadinstitute.hellbender.utils.tsv.TableColumnCollection;
import org.broadinstitute.hellbender.utils.tsv.TableUtils;
import org.broadinstitute.hellbender.utils.tsv.TableWriter;
import org.codehaus.plexus.util.StringUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class is very versatile, but as a result, it must do some lazy loading after it receives the first write command.
 *
 * IMPORTANT:  If this class is not given any command to write(...), the output file will be empty.
 *
 * This class assumes that funcotation maps will have the same fields regardless of allele.
 *
 * This class makes no attempt to render VariantContext attribues.  Any VC attributes must be converted into a
 *  Funcotation (and added to the FuncotationMap) before calling write(...).
 */
public class SimpleTsvOutputRenderer extends OutputRenderer {

    private static final Logger logger = LogManager.getLogger(MafOutputRenderer.class);
    public static final String SIMPLE_TSV_OUTPUT_RENDERER_DUMMY_NAME = "SIMPLE_TSV_OUTPUT_RENDERER";

    private TableWriter<LinkedHashMap<String, String>> writer;
    private boolean isWriterInitialized;

    private LinkedHashMap<String, String> columnNameToFuncotationFieldMap;
    private Set<String> excludedOutputFields;
    private Path outputFilePath;
    private final LinkedHashMap<String, String> unaccountedForDefaultAnnotations;
    private final LinkedHashMap<String, String> unaccountedForOverrideAnnotations;

    private final AliasProvider aliasProvider;

    @VisibleForTesting
    SimpleTsvOutputRenderer(final Path outputFilePath,
                                   final LinkedHashMap<String, String> unaccountedForDefaultAnnotations,
                                   final LinkedHashMap<String, String> unaccountedForOverrideAnnotations,
                                   final Set<String> excludedOutputFields, final LinkedHashMap<String, List<String>> columnNameToAliasesMap,
                                   final String toolVersion) {
        super(toolVersion);

        Utils.nonNull(outputFilePath);
        Utils.nonNull(unaccountedForDefaultAnnotations);
        Utils.nonNull(unaccountedForOverrideAnnotations);
        Utils.nonNull(excludedOutputFields);
        Utils.nonNull(columnNameToAliasesMap);
        Utils.nonNull(toolVersion);

        this.excludedOutputFields = excludedOutputFields;
        this.outputFilePath = outputFilePath;
        this.isWriterInitialized = false;
        this.aliasProvider = new AliasProvider(columnNameToAliasesMap);

        this.unaccountedForDefaultAnnotations = unaccountedForDefaultAnnotations;
        this.unaccountedForOverrideAnnotations = unaccountedForOverrideAnnotations;
    }

    // This method uses the fact that the keys of the input are ordered.
    private void initializeWriter(final List<String> columnNames) {
        final TableColumnCollection columns = new TableColumnCollection(columnNames);
        try {
            writer = TableUtils.writer(outputFilePath, columns, (map, dataLine) -> {
                map.keySet().forEach(k -> dataLine.set(k, map.get(k)));
            });
        } catch (final IOException ioe) {
            throw new GATKException("Could not open the simple TSV writer.", ioe);
        }
        isWriterInitialized = true;
    }

    @Override
    public void close() {
        try {
            // Initialize the writer (so that we can write column names and do not just produce a blank file when
            //  write was never called).
            if (!isWriterInitialized) {
                initializeWriter(new ArrayList<>(aliasProvider.getFields()));
            }
            if (writer != null) {
                writer.close();
            }
        } catch (final IOException ioe) {
            throw new GATKException("Could not close the simple TSV output writing.", ioe);
        }
    }

    @Override
    public void write(final VariantContext variant, final FuncotationMap txToFuncotationMap) {
        if (txToFuncotationMap.getTranscriptList().size() > 1) {
            logger.warn("More than one transcript found.  This should be able to render (grouped by transcript), but you may need to do further processing.  No user action needed.");
        }

        // Ensure that all transcript-allele combinations have the same fields inside the matching funcotations.
        if (!txToFuncotationMap.doAllTxAlleleCombinationsHaveTheSameFields()) {
            throw new GATKException.ShouldNeverReachHereException("The funcotation map cannot be written by this simple output renderer.  This is almost certainly an issue for the GATK development team.");
        }

        for (final String txId : txToFuncotationMap.getTranscriptList()) {
            for (final Allele allele : txToFuncotationMap.getAlleles(txId)) {

                // This will create a set of funcotations based on the locatable info of a variant context.
                //  Use the input segment to keep consistency with the input.
                txToFuncotationMap.add(txId, LocatableFuncotationCreator.create(variant, allele, SIMPLE_TSV_OUTPUT_RENDERER_DUMMY_NAME));
            }
        }

        if (!isWriterInitialized) {
            // Developer note:  the "get(0)" is okay, since we know that all transcript-allele combinations have the same fields.
            // Note that columnNameToFuncotationFieldMap will also hold the final say for what columns will get rendered in the end.
            columnNameToFuncotationFieldMap = createColumnNameToFieldNameMap(txToFuncotationMap, txToFuncotationMap.getTranscriptList().get(0), excludedOutputFields,
                    unaccountedForDefaultAnnotations, unaccountedForOverrideAnnotations);
            initializeWriter(new ArrayList<>(columnNameToFuncotationFieldMap.keySet()));
        }
        try {
            for (final String txId : txToFuncotationMap.getTranscriptList()) {
                for (final Allele allele : txToFuncotationMap.getAlleles(txId)) {
                    final LinkedHashMap<String, String> columnNameToValueMap = createColumnNameToValueMap(columnNameToFuncotationFieldMap,
                            txToFuncotationMap, txId, allele, unaccountedForDefaultAnnotations,
                            unaccountedForOverrideAnnotations, excludedOutputFields);
                    writer.writeRecord(columnNameToValueMap);
                }
            }
        } catch (final IOException ioe) {
            throw new GATKException("Could not write to the simple TSV writer.", ioe);
        }
    }

    // Creates a list of desired output fields and a mapping to fields that exist in the funcotations.  Also, applies excluded fields and adds placeholders for
    //  default and override fields
    private LinkedHashMap<String, String> createColumnNameToFieldNameMap(final FuncotationMap funcotationMap, final String txId, final Set<String> excludedFields,
                                                                         final LinkedHashMap<String, String> unaccountedForDefaultAnnotations,
                                                                         final LinkedHashMap<String, String> unaccountedForOverrideAnnotations) {
        final LinkedHashMap<String, String> result = aliasProvider.createColumnNameToFieldNameMap(funcotationMap, txId);


        // Now lets find the columns that are left over and tack those on with their base name.
        final Set<String> allFuncotationFields = funcotationMap.getFieldNames(txId);
        final Set<String> usedFuncotationFields =  new HashSet<>(result.values());
        getLeftoverStrings(allFuncotationFields, usedFuncotationFields).forEach(c -> result.put(c, c));


        // Make a placeholder for leftover default values
        getLeftoverStrings(unaccountedForDefaultAnnotations.keySet(), new HashSet<>(result.keySet()))
                .forEach(c -> result.put(c, c));

        // Make a placeholder for leftover override values
        getLeftoverStrings(unaccountedForOverrideAnnotations.keySet(), new HashSet<>(result.keySet()))
                .forEach(c -> result.put(c, c));

        // Remove the excluded columns
        excludedFields.forEach(e -> result.remove(e));

        return result;
    }

    private List<String> getLeftoverStrings(final Set<String> superset, final Set<String> usedFuncotationFields) {
        final Set<String> leftoverColumns = Sets.difference(superset, usedFuncotationFields);
        return leftoverColumns.stream().sorted().collect(Collectors.toList());
    }

    /**
     * Simple method that will produce a linked field:value hashmap using starting fields from the funcotations in a {@link FuncotationMap}
     *  and a map of funcotation field name.
     *
     *  This method does not add columns for defaults or overrides in the output map.  Just uses the override and
     *  default mappings to look up values.  In other words, this method assumes that the keys in the
     *  columnNameToFieldMap are the only ones of interest.
     *
     * @param columnNameToFieldMap Mapping from the output column name to the funcotation field name in the map.
     *                             Should be generated from
     *                             {@link SimpleTsvOutputRenderer#createColumnNameToFieldNameMap(FuncotationMap, String, Set, LinkedHashMap, LinkedHashMap)}
     * @param funcotationMap a funcotation map from which to extract values
     * @param txId transcript to use
     * @param allele alternate allele to use
     * @param unaccountedDefaultAnnotations default values that are not already represented in the funcotation map.
     * @param unaccountedOverrideAnnotations override values that are not already represented in the funcotation map.
     * @param excludedFields Names of columns to exclude when creating a final map from column name to the value.
     * @return Column names to value mapping.  Keys will match the input columnNameToFieldMap.  Never {@code null}.  If
     * no suitable value is found, this will populate with an empty string ("").
     */
    @VisibleForTesting
    static LinkedHashMap<String, String> createColumnNameToValueMap(final LinkedHashMap<String, String> columnNameToFieldMap,
                                                                      final FuncotationMap funcotationMap, final String txId, final Allele allele,
                                                                            final LinkedHashMap<String, String> unaccountedDefaultAnnotations,
                                                                            final LinkedHashMap<String, String> unaccountedOverrideAnnotations, final Set<String> excludedFields) {

        final LinkedHashMap<String, String> result = new LinkedHashMap<>();

        // Simply grab the funcotation field using the column name to funcotation field map.  Filter out any values.
        // Note that the keys will come out in order, since columnNameToFieldMap is a LinkedHashMap.
        for (final Map.Entry<String, String> entry : columnNameToFieldMap.entrySet()) {
            final String key = entry.getKey();
            final String funcotationFieldName = entry.getValue();

            if (excludedFields.contains(key)) {
                continue;
            }
            final String funcotationMapFieldValue = funcotationMap.getFieldValue(txId, funcotationFieldName, allele);

            // The final value is (in priority order) override annotation, funcotation field value, default annotation,
            //   empty string ("").
            final String finalValue = unaccountedOverrideAnnotations.getOrDefault(key,
                    funcotationMapFieldValue == null ? unaccountedDefaultAnnotations.getOrDefault(key, "")
                            : funcotationMapFieldValue
                    );
            result.put(key, finalValue);
        }

        return result;
    }

    /**
     * Create key value pairs where the key is the output column name and the value is a list of possible field names, in order of priority.
     *
     * @param configFile config file that encodes column_name:alias_1,alias_2 ....
     * @return mapping of output column names to possible field names in order of priority.  Never {@code null}
     */
    @VisibleForTesting
    static LinkedHashMap<String, List<String>> createColumnNameToAliasesMap(final Path configFile) {

        // Use Apache Commons configuration since it will preserve the order of the keys in the config file.
        //  Properties will not preserve the ordering, since it is backed by a HashSet.
        try {
            final Configuration configFileContents = new Configurations().properties(configFile.toFile());
            final List<String> keys = new ArrayList<>();
            configFileContents.getKeys().forEachRemaining(k -> keys.add(k));
            return keys.stream().collect(Collectors.toMap(Function.identity(), k -> Arrays.asList(splitAndTrim(configFileContents.getString(k), ",")),
                (x1, x2) -> {
                    throw new IllegalArgumentException("Should not be able to have duplicate field names."); },
                LinkedHashMap::new ));

        } catch (final ConfigurationException ce) {
            throw new UserException.BadInput("Unable to read from XSV config file: " + configFile.toUri().toString(), ce);
        }
    }

    /**
     * @param outputFilePath {@link Path} to write.  Never {@code null}
     * @param unaccountedForDefaultAnnotations {@link LinkedHashMap} of default annotations that must be added.  Never {@code null}
     * @param unaccountedForOverrideAnnotations {@link LinkedHashMap} of override annotations that must be added.  Never {@code null}
     * @param excludedOutputFields {@link Set} column names that are not to appear in the output file.  Use an empty set
     *                                        for no exclusions.  Never {@code null}
     * @param configPath Configuration file  where each key is a column and a comma-separated list of fields acts as a
     *                   list of possible aliases.  Note that the list of the keys is the same order that will be seen
     *                   in the output.
     * @param toolVersion The version number of the tool used to produce the VCF file.  Never {@code null}
     */
    public static SimpleTsvOutputRenderer createFromFile(final Path outputFilePath,
                                                         final LinkedHashMap<String, String> unaccountedForDefaultAnnotations,
                                                         final LinkedHashMap<String, String> unaccountedForOverrideAnnotations,
                                                         final Set<String> excludedOutputFields, final Path configPath,
                                                         final String toolVersion) {
        return new SimpleTsvOutputRenderer(outputFilePath, unaccountedForDefaultAnnotations, unaccountedForOverrideAnnotations,
                excludedOutputFields, createColumnNameToAliasesMap(configPath), toolVersion);
    }

    /**
     * Use when loading the config file from the jar.
     *
     * See {@link SimpleTsvOutputRenderer#createFromFile(Path, LinkedHashMap, LinkedHashMap, Set, Path, String)}
     *
     * @param outputFilePath See {@link SimpleTsvOutputRenderer#createFromFile(Path, LinkedHashMap, LinkedHashMap, Set, Path, String)}
     * @param unaccountedForDefaultAnnotations See {@link SimpleTsvOutputRenderer#createFromFile(Path, LinkedHashMap, LinkedHashMap, Set, Path, String)}
     * @param unaccountedForOverrideAnnotations See {@link SimpleTsvOutputRenderer#createFromFile(Path, LinkedHashMap, LinkedHashMap, Set, Path, String)}
     * @param excludedOutputFields See {@link SimpleTsvOutputRenderer#createFromFile(Path, LinkedHashMap, LinkedHashMap, Set, Path, String)}
     * @param resourcePath Configuration file (as a Resource).  See {@link SimpleTsvOutputRenderer#createFromFile(Path, LinkedHashMap, LinkedHashMap, Set, Path, String)}
     * @param toolVersion See {@link SimpleTsvOutputRenderer#createFromFile(Path, LinkedHashMap, LinkedHashMap, Set, Path, String)}
     */
    public static SimpleTsvOutputRenderer createFromResource(final Path outputFilePath,
                                                         final LinkedHashMap<String, String> unaccountedForDefaultAnnotations,
                                                         final LinkedHashMap<String, String> unaccountedForOverrideAnnotations,
                                                         final Set<String> excludedOutputFields, final Path resourcePath,
                                                         final String toolVersion) {
        try {
            return new SimpleTsvOutputRenderer(outputFilePath, unaccountedForDefaultAnnotations, unaccountedForOverrideAnnotations,
                    excludedOutputFields, createColumnNameToAliasesMap(Resource.getResourceContentsAsFile(resourcePath.toString()).toPath()), toolVersion);
        } catch (final IOException ioe) {
            throw new GATKException.ShouldNeverReachHereException("Could not read config file: " + resourcePath,
                    ioe);
        }
    }

    @VisibleForTesting
    static String[] splitAndTrim( String text, String separator ) {
        return Stream.of(StringUtils.split(text, separator)).map(s -> s.trim()).toArray(String[]::new);
    }

    @VisibleForTesting
    LinkedHashMap<String, String> getColumnNameToFuncotationFieldMap() {
        return columnNameToFuncotationFieldMap;
    }
}
