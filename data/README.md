# Real-World Datasets

This directory holds real-world data files used for benchmarking. Files are not committed to git due to size.

## Wikipedia Pageview Logs

**Source:** https://dumps.wikimedia.org/other/pageviews/

Download any hourly dump (each ~40MB compressed):

```bash
curl -sL "https://dumps.wikimedia.org/other/pageviews/2026/2026-04/pageviews-20260414-010000.gz" \
  -o data/pageviews-sample.gz
```

**Format:** `domain page_title view_count bytes_served` per line.

**Usage in code:**
```java
String[] keys = WikipediaDataLoader.loadPageTitles("data/pageviews-sample.gz", 100000);
```

## CAIDA Network Traces (Optional)

**Source:** https://data.caida.org/ (requires institutional access request)

Extract 5-tuple flow keys from anonymized packet headers. Used by MemC3 and Cuckoo++ papers.
