# solution 1
curl -X POST  -k -H 'Content-Type: application/json' https://elastic:changeme@ag18-cdf-single.ad.interset.com:31092/risk_scores\*/_search\?pretty  -d \
'{
    "query": {
        "bool" : {
            "must" : [
                {
                    "bool":{
                        "should":[
                            {
                                "match":{
                                    "entityType":"usr"
                                }
                            }
                        ]
                    }
                }
            ],
            "should":[
                {
                    "bool":{
                        "should":[
                            {
                                "bool":{
                                    "must":{
                                        "exists": {
                                            "field": "hasAnomalies"
                                        }
                                    }
                                }
                            },
                            {
                                "match":{
                                    "hasAnomalies":true
                                }
                            }
                        ]
                    }
                },
                {
                    "bool":{
                        "must_not":{
                            "match":{
                                "score":0
                            }
                        }
                    }
                }
            ],
            "minimum_should_match" : 1
        }
    },
    "size":0,
    "aggs":{
        "first":{
            "terms":{
                "field":"entityHash","size":10000
            },
            "aggs":{
                "second":{
                    "max":{
                        "field":"score"
                    }
                },
                "third": {
                    "top_hits": {
                        "_source": {
                            "includes": [ "timestamp", "score","hasAnomalies", "entityName","entityHash","entityType" ]
                        },
                        "size": 1
                    }
                },
                "forth": {
                    "bucket_sort": {
                        "sort": [
                            { "second": { "order": "desc" } }
                        ],
                        "from":0,
                        "size":10
                    }
                }
            }
        }
    }
}'