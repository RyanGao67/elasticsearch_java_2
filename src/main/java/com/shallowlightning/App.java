package com.shallowlightning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.*;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.NumericMetricsAggregation;
import org.elasticsearch.search.aggregations.metrics.TopHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static org.elasticsearch.index.query.QueryBuilders.*;

public class App {
    private RestHighLevelClient client = SingletonClient.getClient();

    public static void main(String[] args) throws IOException {

//        // create index
//        new App().createIndex();
//
//        // populate the index
//        Map<String, String> metadata = new HashMap();
//        metadata.put("routing", "entityHash");
//        metadata.put("id", "id");
//        new App().index("sample", "risk_scores", metadata);


        long start;long finish;

        start =System.currentTimeMillis();
        new App().search1();
        finish =System.currentTimeMillis();
        System.out.println("Origin: "+(finish-start));

        start =System.currentTimeMillis();
        new App().search2();
        finish =System.currentTimeMillis();
        System.out.println("Solution: "+(finish-start));


        // close the client
        new App().client.close();
    }

    public void createIndex(){
        // initialize request
        CreateIndexRequest request = new CreateIndexRequest("risk_scores");
        // source request
        String a = "{ \"settings\": { \"number_of_shards\": \"2\", \"number_of_replicas\": \"1\" }, \"mappings\" : { \"properties\" : { \"entityName\" : { \"type\" : \"text\", \"fields\" : {\"raw\" : {\"type\" : \"keyword\"}} }, \"entityType\" : { \"type\" : \"text\", \"fields\" : {\"raw\" : {\"type\" : \"keyword\"}} }, \"entityHash\" : {\"type\" : \"keyword\"},\"hasAnomalies\" : {\"type\" : \"boolean\"}, \"id\" : {\"type\" : \"keyword\"}, \"score\" : {\"type\" : \"double\"}, \"timestamp\" : {\"type\" : \"date\"}} }}\n" ;
        request.source(a, XContentType.JSON);
        // create index with request
        ActionListener<CreateIndexResponse> listener = new ActionListener<CreateIndexResponse>() {
            @Override
            public void onResponse(CreateIndexResponse createIndexResponse) {
                boolean acknowledged = createIndexResponse.isAcknowledged();
                boolean shardsAcknowledged = createIndexResponse.isShardsAcknowledged();
                System.out.println("acknowledged: "+acknowledged);
                System.out.println("shardsAcknowledged: "+shardsAcknowledged);
            }
            @Override
            public void onFailure(Exception e) {}
        };
        App app = new App();
        app.client.indices().createAsync(request, RequestOptions.DEFAULT, listener);
    }

    public void index(String document, String indexName, Map<String, String > metadata) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        String doc = IOUtils.toString(
                App.class.getResourceAsStream(String.format("/%s.json", document)),
                StandardCharsets.UTF_8
        );
        JsonNode jsonDocuments = mapper.readTree(doc);
        jsonDocuments.fields().forEachRemaining(jsonNode -> index(jsonNode.getValue(), indexName, metadata));
    }

    public void index(JsonNode jsonDocuments, String index, Map<String, String> metadata){
        int id = 0;
        for(JsonNode node:jsonDocuments){
            System.out.println(node.toString());
            id++;
            IndexRequest indexRequest = new IndexRequest(index);
            // source the indexrequest
            indexRequest.source(node.toString(), XContentType.JSON);
            // get the metadata for this doc: id
            if(metadata.containsKey("id") && node.has(metadata.get("id"))){
                String docId = node.get(metadata.get("id")).asText();
                indexRequest.id(docId);
            }else{
                indexRequest.id(String.valueOf(id));
            }
            // get the metadata for this doc: routing
            if(metadata.containsKey("routing") && node.has(metadata.get("routing"))){
                indexRequest.routing(node.get(metadata.get("routing")).asText());
            }else if(index.startsWith("anomalies")){
                String routingKey = "id";
                if(node.has("rollupLevel") && node.get("rollupLevel").has("parent")){
                    routingKey = "aggId";
                }
                indexRequest.routing(node.get(routingKey).asText());
            }
            // if document has parent, add it to routing
            if(metadata.containsKey("parent") && node.has(metadata.get("parent"))){
                String parentId = node.get(metadata.get("parent")).asText();
                indexRequest.routing(parentId);
            }
            try{
                client.index(indexRequest,RequestOptions.DEFAULT);
            }catch (IOException e){
                throw new RuntimeException("Failed to index document with index: "+index);
            }
        }
    }

    public List<TopRiskyEntity> parse(Aggregations aggregations) {
        List<TopRiskyEntity> topRiskyEntities = new ArrayList<>();
        Terms riskAgg = aggregations.get("entities");
        if (riskAgg == null) {return topRiskyEntities;}
        for (Terms.Bucket entity : riskAgg.getBuckets()) {
            Aggregations entityAggs = entity.getAggregations();
            TopRiskyEntity topRisky = new TopRiskyEntity();
            topRisky.setEntityHash(entity.getKeyAsString()); // get the bucket key ---- entityhash
            TopHits mostCurrent = entityAggs.get("currentAgg");
            SearchHit[] hits = mostCurrent.getHits().getHits();
            if (hits.length > 0) {
                Map<String, Object> mostCurrentHit = mostCurrent.getHits().getHits()[0].getSourceAsMap();
                topRisky.setEntityName(mostCurrentHit.get("entityName").toString());
                topRisky.setEntityType(mostCurrentHit.get("entityType").toString());
                topRisky.setTimestamp(((Number) mostCurrentHit.get("timestamp")).longValue());
                topRisky.setAnomalous(Boolean.parseBoolean(mostCurrentHit.get("hasAnomalies").toString()));
                NumericMetricsAggregation.SingleValue score = entityAggs.get("scoreAgg");
                topRisky.setScore(Double.parseDouble(score.getValueAsString()));
            }
            topRiskyEntities.add(topRisky);
        }
        return topRiskyEntities;
    }

    public void  withEntityTypes(List<EntityType> types, BoolQueryBuilder query) {
        if (types != null) {
            BoolQueryBuilder tempBoolQuery = boolQuery();
            types.forEach(type -> tempBoolQuery.should(matchQuery("entityType", type.getCode())));
            query.must(tempBoolQuery);
        }
    }

    public void includeNonAnomalous(boolean includeAnomalous, BoolQueryBuilder query) {
        if (!includeAnomalous) {
            query.should(
                    boolQuery()
                            .should(boolQuery().must(existsQuery("hasAnomalies")))
                            .should(matchQuery("hasAnomalies", true))
            ).should(
                    boolQuery()
                            .mustNot(matchQuery("score", 0))
            ).minimumShouldMatch(1);
        }
    }


    public void search1() throws IOException {
        SearchRequest searchRequest = new SearchRequest("risk_scores*");
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

        // Controls how to deal with unavailable concrete indices (closed or missing),
        // how wildcard expressions are expanded to actual indices (all, closed or open indices)
        // and how to deal with wildcard expressions that resolve to no indices.
        // fromOptions(boolean ignoreUnavailable, boolean allowNoIndices, boolean expandToOpenIndices, boolean expandToClosedIndices)
        searchRequest.indicesOptions(IndicesOptions.fromOptions(true, true, true, true));
        searchRequest.requestCache(true);
        searchSourceBuilder.trackTotalHits(true);

        // aggregation
        TermsAggregationBuilder agg = AggregationBuilders.terms("entities").field("entityHash").size(10);
        agg.subAggregation(
                AggregationBuilders.max("scoreAgg").field("score")
        ).subAggregation(
                AggregationBuilders
                        .topHits("currentAgg")
                        .fetchSource(new String[]{"entityName", "entityType", "timestamp", "hasAnomalies"}, null)
                        .size(1)
        ).order(
                BucketOrder.compound(
                        BucketOrder.aggregation("scoreAgg", "value", false), // sort by scoreAgg first
                        BucketOrder.key(false)                                                   // key as the tie breaker
                )
        );
        searchSourceBuilder.aggregation(agg);

        // create boolean query builder
        BoolQueryBuilder boolQueryBuilder = boolQuery();
        withEntityTypes(Collections.singletonList(EntityType.user), boolQueryBuilder);
        includeNonAnomalous(false, boolQueryBuilder);
        // filter bool query builder, no need for score(google bool filter query)
        searchSourceBuilder.query(boolQuery().filter(boolQueryBuilder));
        searchSourceBuilder.size(0).from(0).version(false);

        SearchRequest request= searchRequest.source(searchSourceBuilder);
        SearchResponse searchResponse = client.search(request, RequestOptions.DEFAULT);

        // print hit index for test
        // SearchHit[] result = searchResponse.getHits().getHits();for(SearchHit hit:result){String s = hit.getSourceAsString();System.out.println(s);}

        // get aggregations
        List<TopRiskyEntity> topRiskyEntityList = parse(searchResponse.getAggregations());
        LinkedHashSet<String> topRiskyEntities = topRiskyEntityList
                .stream()
                .map(TopRiskyEntity::getEntityHash)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        System.out.println(topRiskyEntities.toString());
    }
    public void search2() throws IOException {
        SearchRequest searchRequest = new SearchRequest("risk_scores*");
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchRequest.indicesOptions(IndicesOptions.fromOptions(true, true, true, true));
        searchRequest.requestCache(true);
        searchSourceBuilder.trackTotalHits(true);
        // aggregation
        TermsAggregationBuilder agg = AggregationBuilders.terms("entities").field("entityHash").size(10);
        agg.subAggregation(
                AggregationBuilders.max("scoreAgg").field("score")
        ).subAggregation(
                AggregationBuilders
                        .topHits("currentAgg")
                        .fetchSource(new String[]{"entityName", "entityType", "timestamp", "hasAnomalies"}, null)
                        .size(1)
        ).subAggregation(
                PipelineAggregatorBuilders.bucketSort(
                        "bucketHit",
                        Arrays.asList(new FieldSortBuilder("scoreAgg").order(SortOrder.DESC))
                ).from(0).size(10)
        );
        searchSourceBuilder.aggregation(agg);
        // create boolean query builder
        BoolQueryBuilder boolQueryBuilder = boolQuery();
        withEntityTypes(Collections.singletonList(EntityType.user), boolQueryBuilder);
        includeNonAnomalous(false, boolQueryBuilder);
        // filter bool query builder, no need for score(google bool filter query)
        searchSourceBuilder.query(boolQuery().filter(boolQueryBuilder));
        searchSourceBuilder.size(0).from(0).version(false);
        //////////////////////////////////////////////////////////////////////////////////////////////////
        SearchRequest request= searchRequest.source(searchSourceBuilder);
        SearchResponse searchResponse = client.search(request, RequestOptions.DEFAULT);
        //////////////////////////////////////////////////////////////////////////////////////////////////
        // get aggregations
        List<TopRiskyEntity> topRiskyEntityList = parse(searchResponse.getAggregations());
        LinkedHashSet<String> topRiskyEntities = topRiskyEntityList
                .stream()
                .map(TopRiskyEntity::getEntityHash)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        System.out.println(topRiskyEntities.toString());
    }
}
