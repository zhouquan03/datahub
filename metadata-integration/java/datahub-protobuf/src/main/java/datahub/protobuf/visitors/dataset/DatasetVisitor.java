package datahub.protobuf.visitors.dataset;

import com.linkedin.common.GlobalTags;
import com.linkedin.common.GlossaryTermAssociation;
import com.linkedin.common.GlossaryTermAssociationArray;
import com.linkedin.common.GlossaryTerms;
import com.linkedin.common.InstitutionalMemory;
import com.linkedin.common.InstitutionalMemoryMetadata;
import com.linkedin.common.InstitutionalMemoryMetadataArray;
import com.linkedin.common.TagAssociation;
import com.linkedin.common.TagAssociationArray;
import com.linkedin.common.urn.DatasetUrn;
import com.linkedin.data.template.RecordTemplate;
import com.linkedin.data.template.StringMap;
import com.linkedin.dataset.DatasetProperties;
import com.linkedin.events.metadata.ChangeType;
import datahub.protobuf.model.ProtobufGraph;
import datahub.protobuf.visitors.ProtobufModelVisitor;
import datahub.protobuf.visitors.VisitContext;
import datahub.event.MetadataChangeProposalWrapper;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Builder
@AllArgsConstructor
public class DatasetVisitor implements ProtobufModelVisitor<MetadataChangeProposalWrapper<? extends RecordTemplate>> {
    @Builder.Default
    private final List<ProtobufModelVisitor<InstitutionalMemoryMetadata>> institutionalMemoryMetadataVisitors = List.of();
    @Builder.Default
    private final List<ProtobufModelVisitor<DatasetProperties>> datasetPropertyVisitors = List.of();
    @Builder.Default
    private final List<ProtobufModelVisitor<TagAssociation>> tagAssociationVisitors = List.of();
    @Builder.Default
    private final List<ProtobufModelVisitor<GlossaryTermAssociation>> termAssociationVisitors = List.of();
    @Builder.Default
    private final String protocBase64 = "";
    @Builder.Default
    private final ProtobufModelVisitor<String> descriptionVisitor = new DescriptionVisitor();

    @Override
    public Stream<MetadataChangeProposalWrapper<? extends RecordTemplate>> visitGraph(VisitContext context) {
        final String datasetUrn = context.getDatasetUrn().toString();
        final ProtobufGraph g = context.getGraph();

        return Stream.of(
                new MetadataChangeProposalWrapper<>(DatasetUrn.ENTITY_TYPE, datasetUrn, ChangeType.UPSERT, new DatasetProperties()
                        .setDescription(g.accept(context, List.of(descriptionVisitor)).collect(Collectors.joining("\n")))
                        .setCustomProperties(new StringMap(
                                Stream.concat(
                                                Stream.of(Map.entry("protoc", protocBase64)),
                                                g.accept(context, datasetPropertyVisitors).flatMap(props -> props.getCustomProperties().entrySet().stream()))
                                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                        )), "datasetProperties"),
                new MetadataChangeProposalWrapper<>(DatasetUrn.ENTITY_TYPE, datasetUrn, ChangeType.UPSERT, new InstitutionalMemory().setElements(
                        new InstitutionalMemoryMetadataArray(
                                g.accept(context, institutionalMemoryMetadataVisitors)
                                        .map(inst -> inst.setCreateStamp(context.getAuditStamp()))
                                        .collect(Collectors.toMap(InstitutionalMemoryMetadata::getUrl, Function.identity(),
                                                (a1, a2) -> a1, LinkedHashMap::new))
                                        .values()
                    )), "institutionalMemory"),
                new MetadataChangeProposalWrapper<>(DatasetUrn.ENTITY_TYPE, datasetUrn, ChangeType.UPSERT,
                        new GlobalTags().setTags(new TagAssociationArray(
                                g.accept(context, tagAssociationVisitors).collect(Collectors.toList())
                    )), "globalTags"),
                new MetadataChangeProposalWrapper<>(DatasetUrn.ENTITY_TYPE, datasetUrn, ChangeType.UPSERT,
                        new GlossaryTerms().setTerms(new GlossaryTermAssociationArray(
                                g.accept(context, termAssociationVisitors).collect(Collectors.toList())
                    )).setAuditStamp(context.getAuditStamp()), "glossaryTerms")
        );
    }
}
