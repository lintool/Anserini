# Anserini: BM25 Baselines for MS MARCO Passage Ranking

This page contains instructions for running BM25 baselines on the [MS MARCO *passage* ranking task](https://microsoft.github.io/msmarco/).
Note that there is a separate [MS MARCO *document* ranking task](experiments-msmarco-doc.md).
We also have a [separate page](experiments-doc2query.md) describing document expansion experiments (doc2query) for this task.

**Setup Note:** If you're instantiating an Ubuntu VM on your system or on cloud (AWS and GCP) for this particular task, try to provision enough resources as the tasks could take some time to finish such as RAM > 6GB and storage ~ 100 GB (SSD). This will prevent going back and fixing machine configuration again and again.

If you're a Waterloo undergraduate going through this guide as the [screening exercise](https://github.com/lintool/guide/blob/master/ura.md) of joining my research group, try to understand what you're actually doing, instead of simply [cargo culting](https://en.wikipedia.org/wiki/Cargo_cult_programming) (i.e., blinding copying and pasting commands into a shell).
In particular, you'll want to pay attention to the "What's going on here?" sections.

<details>
<summary>What's going on here?</summary>

As a really high level summary: in the MS MARCO passage ranking task, you're given a bunch of passages to search and a bunch of queries.
The system's task is to return the best passages for each query (i.e., passages that are relevant).

Note that "the things you're searching" are called documents (in the generic sense), even though they're actually passages (extracted from web pages) in this case.
You could be search web pages, PDFs, Excel spreadsheets, and even podcasts.
Information retrieval researchers refer to these all as "documents".
</details>


## Data Prep

We're going to use the repository's root directory as the working directory.
First, we need to download and extract the MS MARCO passage dataset:

```bash
mkdir collections/msmarco-passage

wget https://msmarco.blob.core.windows.net/msmarcoranking/collectionandqueries.tar.gz -P collections/msmarco-passage

# Alternative mirror:
# wget https://www.dropbox.com/s/9f54jg2f71ray3b/collectionandqueries.tar.gz -P collections/msmarco-passage

tar xvfz collections/msmarco-passage/collectionandqueries.tar.gz -C collections/msmarco-passage
```

To confirm, `collectionandqueries.tar.gz` should have MD5 checksum of `31644046b18952c1386cd4564ba2ae69`.

<details>
<summary>What's going on here?</summary>

If you peak inside the collection:

```bash
head collections/msmarco-passage/collection.tsv
```

You'll see that `collection.tsv` contains the passages that we're searching.
Each line represents a passage:
the first column contains a unique identifier for the passage (called the `docid`) and the second column contains the text of the passage itself.

</details>

Next, we need to convert the MS MARCO tsv collection into Anserini's jsonl files (which have one json object per line):

```bash
python tools/scripts/msmarco/convert_collection_to_jsonl.py \
 --collection-path collections/msmarco-passage/collection.tsv \
 --output-folder collections/msmarco-passage/collection_jsonl
```

The above script should generate 9 jsonl files in `collections/msmarco-passage/collection_jsonl`, each with 1M lines (except for the last one, which should have 841,823 lines).


## Indexing

We can now index these docs as a `JsonCollection` using Anserini:

```bash
sh target/appassembler/bin/IndexCollection -threads 9 -collection JsonCollection \
 -generator DefaultLuceneDocumentGenerator -input collections/msmarco-passage/collection_jsonl \
 -index indexes/msmarco-passage/lucene-index-msmarco -storePositions -storeDocvectors -storeRaw 
```

Upon completion, we should have an index with 8,841,823 documents.
The indexing speed may vary; on a modern desktop with an SSD, indexing takes a couple of minutes.


## Retrieval

Since queries of the set are too many (+100k), it would take a long time to retrieve all of them. To speed this up, we use only the queries that are in the qrels file: 

```bash
python tools/scripts/msmarco/filter_queries.py \
 --qrels collections/msmarco-passage/qrels.dev.small.tsv \
 --queries collections/msmarco-passage/queries.dev.tsv \
 --output collections/msmarco-passage/queries.dev.small.tsv
```

The output queries file should contain 6980 lines.

<details>
<summary>What's going on here?</summary>

Check out the contents of the queries file:

```bash
$ head collections/msmarco-passage/queries.dev.small.tsv
1048585	what is paula deen's brother
2	 Androgen receptor define
524332	treating tension headaches without medication
1048642	what is paranoid sc
524447	treatment of varicose veins in legs
786674	what is prime rate in canada
1048876	who plays young dr mallard on ncis
1048917	what is operating system misconfiguration
786786	what is priority pass
524699	tricare service number
```

These are the queries we're going to feed to the search engine.
The first field is a unique identifier for the query (called the `qid`) and the second column is the query itself.
These queries are taken from Bing search logs, so they're "realistic" web queries in that they may be ambiguous, contain typos, etc.
</details>

We can now perform a retrieval run using this smaller set of queries:

```bash
sh target/appassembler/bin/SearchMsmarco -hits 1000 -threads 1 \
 -index indexes/msmarco-passage/lucene-index-msmarco \
 -queries collections/msmarco-passage/queries.dev.small.tsv \
 -output runs/run.msmarco-passage.dev.small.tsv
```

Note that by default, the above script uses BM25 with tuned parameters `k1=0.82`, `b=0.68`.
The option `-hits` specifies the number of documents per query to be retrieved.
Thus, the output file should have approximately 6980 × 1000 = 6.9M lines.

Retrieval speed will vary by machine:
On a modern desktop with an SSD, we can get ~0.07 s/query, so the run should finish in under ten minutes.
We can perform multi-threaded retrieval by changing the `-threads` argument.

<details>
<summary>What's going on here?</summary>

Congratulations, you've performed your first retrieval run!

You feed a search engine a bunch of queries, and the retrieval run is the output of the search engine.
For each query, the search engine gives back a ranked list of results (i.e., a list of hits).

Let's take a look:

```bash
$ head runs/run.msmarco-passage.dev.small.tsv
1048585	7187158	1
1048585	7187157	2
1048585	7187163	3
1048585	7546327	4
1048585	7187160	5
1048585	8227279	6
1048585	7617404	7
1048585	7187156	8
1048585	2298838	9
1048585	7187155	10
```

The first column is the `qid` (corresponding to the query).
From above, we can see that `qid` 1048585 is the query "what is paula deen's brother".
The second column is the `docid` of the retrieved result (i.e., the hit), and the third column is the rank position.
That is, in a search interface, `docid` 7187158 would be shown in the top position, `docid` 7187157 would be shown in the second position, etc.

You can grep through the collection to see what that actual passage is:

```bash
$ grep 7187158 collections/msmarco-passage/collection.tsv
7187158	Paula Deen and her brother Earl W. Bubba Hiers are being sued by a former general manager at Uncle Bubba'sâ¦ Paula Deen and her brother Earl W. Bubba Hiers are being sued by a former general manager at Uncle Bubba'sâ
```

In this case, the hit seems relevant.
That is, it answers the query.
So here, the search engine did well.

Note that this particular passage is a bit dirty (garbage characters, dups, etc.)... but that's pretty much a fact of life when you're dealing with the web.

</details>

Finally, we can evaluate the retrieved documents using this the official MS MARCO evaluation script: 

```bash
python tools/scripts/msmarco/msmarco_passage_eval.py \
 collections/msmarco-passage/qrels.dev.small.tsv runs/run.msmarco-passage.dev.small.tsv
```

And the output should be like this:

```
#####################
MRR @10: 0.18741227770955546
QueriesRanked: 6980
#####################
```

<details>
<summary>What's going on here?</summary>

So how do we know if a search engine is any good?
One method is manual examination, which is what we did above.
That is, we actually looked at the results by hand.

Obviously, this isn't scalable if we want to evaluate lots of queries...
If only someone told us which documents were relevant to which queries...

Well, someone has! (Specifically, human editors hired by Microsoft Bing in this case.)
These are captured in what are known as relevance judgments.
Take a look:

```bash
$ grep 1048585 collections/msmarco-passage/qrels.dev.tsv
1048585	0	7187158	1
```

This says that `docid` 7187158 is relevant to `qid` 1048585, which confirms our intuition above.
The file is in what is known as the qrels format.
You can ignore the second column.
The fourth column "1", says that the `docid` is relevant.
In some cases (though not here), that column might say "0", i.e., that the `docid` is _not_ relevant.

With relevance judgments (qrels), we can now automatically evaluate the search engine output (i.e., the run).
The final ingredient we need is a metric (i.e., how to score).

Here, we're using a metric called MRR, or mean reciprocal rank.
The idea is quite simple:
We look at where the relevant `docid` appears.
If it appears at rank 1, the system gets a score of one.
If it appears at rank 2, the system gets a score of 1/2.
If it appears at rank 3, the system gets a score of 1/3.
And so on.
MRR@10 means that we only go down to rank 10.
If the relevant `docid` doesn't appear in the top 10, then the system gets a score of zero.

That's the score of a query.
We take the average of the scores across all queries (6980 in this case), and we arrive at the score for the entire run.
</details>

You can find this run on the [MS MARCO Passage Ranking Leaderboard](https://microsoft.github.io/msmarco/) as the entry named "BM25 (Lucene8, tuned)", dated 2019/06/26.
So you've just reproduced (part of) a leaderboard submission!

We can also use the official TREC evaluation tool, `trec_eval`, to compute other metrics than MRR@10. 
For that we first need to convert runs and qrels files to the TREC format:

```bash
python tools/scripts/msmarco/convert_msmarco_to_trec_run.py \
 --input runs/run.msmarco-passage.dev.small.tsv \
 --output runs/run.msmarco-passage.dev.small.trec

python tools/scripts/msmarco/convert_msmarco_to_trec_qrels.py \
 --input collections/msmarco-passage/qrels.dev.small.tsv \
 --output collections/msmarco-passage/qrels.dev.small.trec
```

And run the `trec_eval` tool:

```bash
tools/eval/trec_eval.9.0.4/trec_eval -c -mrecall.1000 -mmap \
 collections/msmarco-passage/qrels.dev.small.trec runs/run.msmarco-passage.dev.small.trec
```

The output should be:

```
map                   	all	0.1957
recall_1000           	all	0.8573
```

Average precision and recall@1000 are the two metrics we care about the most.

<details>
<summary>What's going on here?</summary>

Don't worry so much about the details here for now.
The tl;dr is that there are different formats for run files and lots of different metrics you can compute.
`trec_eval` is a standard tool used by information retrieval researchers.

In fact, researchers have been trying to answer the question "how do we know if a search result is good and how do we measure it" for over half a century...
and the question still has not been fully resolved.
In short, it's complicated.
</details>

## BM25 Tuning

Note that this figure differs slightly from the value reported in [Document Expansion by Query Prediction](https://arxiv.org/abs/1904.08375), which uses the Anserini (system-wide) default of `k1=0.9`, `b=0.4`.

Tuning was accomplished with `tools/scripts/msmarco/tune_bm25.py`, using the queries found [here](https://github.com/castorini/Anserini-data/tree/master/MSMARCO); the basic approach is grid search of parameter values in tenth increments.
There are five different sets of 10k samples (using the `shuf` command).
We tuned on each individual set and then averaged parameter values across all five sets (this has the effect of regularization).
In separate trials, we optimized for:

+ recall@1000, since Anserini output serves as input to downstream rerankers (e.g., based on BERT), and we want to maximize the number of relevant documents the rerankers have to work with;
+ MRR@10, for the case where Anserini output is directly presented to users (i.e., no downstream reranking).

It turns out that optimizing for MRR@10 and MAP yields the same settings.

Here's the comparison between the Anserini default and optimized parameters:

Setting                     | MRR@10 | MAP    | Recall@1000 |
:---------------------------|-------:|-------:|------------:|
Default (`k1=0.9`, `b=0.4`) | 0.1840 | 0.1926 | 0.8526
Optimized for recall@1000 (`k1=0.82`, `b=0.68`) | 0.1874 | 0.1957 | 0.8573
Optimized for MRR@10/MAP (`k1=0.60`, `b=0.62`)  | 0.1892 | 0.1972 | 0.8555

To reproduce these results, the `SearchMsmarco` class above takes `k1` and `b` parameters as command-line arguments, e.g., `-k1 0.60 -b 0.62` (note that the default setting is `k1=0.82` and `b=0.68`).

As mentioned above, the BM25 run with `k1=0.82`, `b=0.68` corresponds to the entry "BM25 (Lucene8, tuned)" dated 2019/06/26 on the [MS MARCO Passage Ranking Leaderboard](https://microsoft.github.io/msmarco/).
The BM25 run with default parameters `k1=0.9`, `b=0.4` roughly corresponds to the entry "BM25 (Anserini)" dated 2019/04/10 (but Anserini was using Lucene 7.6 at the time).

## Reproduction Log[*](reproducibility.md)

+ Results reproduced by [@ronakice](https://github.com/ronakice) on 2019-08-12 (commit [`5b29d16`](https://github.com/castorini/anserini/commit/5b29d1654abc5e8a014c2230da990ab2f91fb340))
+ Results reproduced by [@MathBunny](https://github.com/MathBunny) on 2019-08-12 (commit [`5b29d16`](https://github.com/castorini/anserini/commit/5b29d1654abc5e8a014c2230da990ab2f91fb340))
+ Results reproduced by [@JMMackenzie](https://github.com/JMMackenzie) on 2020-01-08 (commit [`f63cd22`](https://github.com/castorini/anserini/commit/f63cd2275fa5a9d4da2d17e5f983a3308e8b50ce ))
+ Results reproduced by [@edwinzhng](https://github.com/edwinzhng) on 2020-01-08 (commit [`5cc923d`](https://github.com/castorini/anserini/commit/5cc923d5c02777d8b25df32ff2e2a59be5badfdd))
+ Results reproduced by [@LuKuuu](https://github.com/LuKuuu) on 2020-01-15 (commit [`f21137b`](https://github.com/castorini/anserini/commit/f21137b44f1115d25d1ff8ecaf7780c36498c5de))
+ Results reproduced by [@kevinxyc1](https://github.com/kevinxyc1) on 2020-01-18 (commit [`798cb3a`](https://github.com/castorini/anserini/commit/798cb3a1a86f39317a5e5badb209795826d2033d))
+ Results reproduced by [@nikhilro](https://github.com/nikhilro) on 2020-01-21 (commit [`631589e`](https://github.com/castorini/anserini/commit/631589e9e08326373f46555e007e6c302c19126d))
+ Results reproduced by [@yuki617](https://github.com/yuki617) on 2020-03-29 (commit [`074723c`](https://github.com/castorini/anserini/commit/074723cbb10660fb9be2bfe6325739ab5fe0dd8d))
+ Results reproduced by [@weipang142857](https://github.com/weipang142857) on 2020-04-20 (commit [`074723c`](https://github.com/castorini/anserini/commit/074723cbb10660fb9be2bfe6325739ab5fe0dd8d))
+ Results reproduced by [@HangCui0510](https://github.com/HangCui0510) on 2020-04-23 (commit [`0ae567d`](https://github.com/castorini/anserini/commit/0ae567df5c8a70ac211efd958c9ca1ff609ff782))
+ Results reproduced by [@x65han](https://github.com/x65han) on 2020-04-25 (commit [`f5496b9`](https://github.com/castorini/anserini/commit/f5496b905246084070f959e59626c6323210c3f2))
+ Results reproduced by [@y276lin](https://github.com/y276lin) on 2020-04-26 (commit [`8f48f8e`](https://github.com/castorini/anserini/commit/8f48f8e40a37e5f6b5910a3a3b5c050a0f9be914))
+ Results reproduced by [@stephaniewhoo](http://github.com/stephaniewhoo) on 2020-04-26 (commit [`8f48f8e`](https://github.com/castorini/anserini/commit/8f48f8e40a37e5f6b5910a3a3b5c050a0f9be914))
+ Results reproduced by [@eiston](http://github.com/eiston) on 2020-05-04 (commit [`dd84a5a`](https://github.com/castorini/anserini/commit/dd84a5a514700365d9aa4a1ea988107372515f33))
+ Results reproduced by [@rohilg](http://github.com/rohilg) on 2020-05-09 (commit [`20ee950`](https://github.com/castorini/anserini/commit/20ee950fbdc5cc9ce1c993911cbca4fcbfa86d02))
+ Results reproduced by [@wongalvis14](https://github.com/wongalvis14) on 2020-05-09 (commit [`ebac5d6`](https://github.com/castorini/anserini/commit/ebac5d62f2e626e0a48c83dad79bddba60cadcf5))
+ Results reproduced by [@YimingDou](https://github.com/YimingDou) on 2020-05-14 (commit [`3b0a642`](https://github.com/castorini/anserini/commit/3b0a6420e49863d9fe5908cf6e99582eb2d2882e))
+ Results reproduced by [@richard3983](https://github.com/richard3983) on 2020-05-14 (commit [`a65646f`](https://github.com/castorini/anserini/commit/a65646fe203bf5c9c32189a56082d6f4d3bc340d))
+ Results reproduced by [@MXueguang](https://github.com/MXueguang) on 2020-05-20 (commit [`3b2751e`](https://github.com/castorini/anserini/commit/3b2751e2d02a9d530e1c3d30b91083faeece8982))
+ Results reproduced by [@shaneding](https://github.com/shaneding) on 2020-05-23 (commit [`b6e0367`](https://github.com/castorini/anserini/commit/b6e0367ef4e2b4fce9d81c8397ef1188e35971e7))
+ Results reproduced by [@adamyy](https://github.com/adamyy) on 2020-05-28 (commit [`94893f1`](https://github.com/castorini/anserini/commit/94893f170e047d77c3ef5b8b995d7fbdd13f4298))
+ Results reproduced by [@kelvin-jiang](https://github.com/kelvin-jiang) on 2020-05-28 (commit [`d55531a`](https://github.com/castorini/anserini/commit/d55531a738d2cf9e14c376d798d2de4bd3020b6b))
+ Results reproduced by [@TianchengY](https://github.com/TianchengY) on 2020-05-28 (commit [`2947a16`](https://github.com/castorini/anserini/commit/2947a1622efae35637b83e321aba8e6fccd43489))
+ Results reproduced by [@stariqmi](https://github.com/stariqmi) on 2020-05-28 (commit [`4914305`](https://github.com/castorini/anserini/commit/455169ea6a09f637817a6c4b4f6837dcc845f5f7))
+ Results reproduced by [@justinborromeo](https://github.com/justinborromeo) on 2020-06-10 (commit [`7954eab`](https://github.com/castorini/anserini/commit/7954eab43f17bb8d254987d5873933c0b9596bb4))
+ Results reproduced by [@yxzhu16](https://github.com/yxzhu16) on 2020-07-03 (commit [`68ace26`](https://github.com/castorini/anserini/commit/68ace26d0418a769df3d2b21e946495e54d462f6))
+ Results reproduced by [@LizzyZhang-tutu](https://github.com/LizzyZhang-tutu) on 2020-07-13 (commit [`8c98d5b`](https://github.com/castorini/anserini/commit/8c98d5ba0795bbea01bcef1e21abb153fe4c3da1))
+ Results reproduced by [@estella98](https://github.com/estella98) on 2020-07-29 
(commit [`99092a8`](https://github.com/castorini/anserini/commit/99092a82179d7efd38fc0b8c7c967137a40cd96f))
+ Results reproduced by [@tangsaidi](https://github.com/tangsaidi) on 2020-08-19 
(commit [`aba846`](https://github.com/castorini/anserini/commit/aba846aa07d6f319fb3dc9cb591c20b4ae69f9ef))
+ Results reproduced by [@qguo96](https://github.com/qguo96) on 2020-09-07 (commit [`e16b3c1`](https://github.com/castorini/anserini/commit/e16b3c160664057d4e00f2b4030cb6cb0d32fabd))
+ Results reproduced by [@yuxuan-ji](https://github.com/yuxuan-ji) on 2020-09-08 (commit [`0f9a8ec`](https://github.com/castorini/anserini/commit/0f9a8ec4f335fb49c9387351745d1c755afc0e84))
+ Results reproduced by [@wiltan-uw](https://github.com/wiltan-uw) on 2020-09-09 (commit [`93d913f`](https://github.com/castorini/anserini/commit/93d913f2619c44451be3d46816c7b9c44cbeb091))
+ Results reproduced by [@JeffreyCA](https://github.com/JeffreyCA) on 2020-09-13 (commit [`bc2628b`](https://github.com/castorini/anserini/commit/bc2628b9916ce42b8026497c695d4c4198547f04))
+ Results reproduced by [@jhuang265](https://github.com/jhuang265) on 2020-10-15 (commit [`66711b9`](https://github.com/castorini/anserini/commit/66711b9ff7722e2aea4ce2f59ca26ead5c091cac))
+ Results reproduced by [@rayyang29](https://github.com/rayyang29) on 2020-10-27 (commit [`ad8cc5a`](https://github.com/castorini/anserini/commit/ad8cc5a02a53f09a83a8a6bfd7d187c9c3f96bd5))
+ Results reproduced by [@Dahlia-Chehata](https://github.com/Dahlia-Chehata) on 2020-11-11 (commit [`22c0ad3`](https://github.com/castorini/anserini/commit/22c0ad3ebcff22c34a69ee3a7c122c4a9fb27a0e))
+ Results reproduced by [@rakeeb123](https://github.com/rakeeb123) on 2020-12-07 (commit [`f50dcce`](https://github.com/castorini/anserini/commit/f50dcceb6cd0ec3403c1e77066aa51bb3275d24e))
+ Results reproduced by [@jrzhang12](https://github.com/jrzhang12) on 2021-01-02 (commit [`be4e44d`](https://github.com/castorini/anserini/commit/be4e44d5ac1e898469fc179f8e6a336234b23d93))
+ Results reproduced by [@HEC2018](https://github.com/HEC2018) on 2021-01-04 (commit [`4de21ec`](https://github.com/castorini/anserini/commit/4de21ece5e53cf20b4fcc711b575606b83c0d1f1))
+ Results reproduced by [@KaiSun314](https://github.com/KaiSun314) on 2021-01-08 (commit [`113f1c7`](https://github.com/castorini/anserini/commit/113f1c78c3ffc8681a06c571901cf9ad8f5ee633))
+ Results reproduced by [@yemiliey](https://github.com/yemiliey) on 2021-01-18 (commit [`179c242`](https://github.com/castorini/anserini/commit/179c242562bbb990e421f315370f34d4d19bbb9f))
+ Results reproduced by [@larryli1999](https://github.com/larryli1999) on 2021-01-22 (commit [`3f9af5`](https://github.com/castorini/anserini/commit/3f9af54d6215eacbade7fc99ff8890920fdddee0))
+ Results reproduced by [@ArthurChen189](https://github.com/ArthurChen189) on 2021-04-08 (commit [`45a5a21`](https://github.com/castorini/anserini/commit/45a5a219af92c82dd429f4d96aee13bd87825147))
+ Results reproduced by [@printfCalvin](https://github.com/printfCalvin) on 2021-04-11 (commit [`d808d4a`](https://github.com/castorini/anserini/commit/d808d4a50ee69f44493e19a87dc36c7eb99402a9))
+ Results reproduced by [@saileshnankani](https://github.com/saileshnankani) on 2021-04-26 (commit [`5781c87`](https://github.com/castorini/anserini/commit/5781c871db12f0e36139982fbf1c805cfec189ee))
+ Results reproduced by [@andrewyguo](https://github.com/andrewyguo) on 2021-04-29 (commit [`71f3ca6`](https://github.com/castorini/anserini/commit/71f3ca671faf6ddd7b0dea0a1e7f4b590a0a02a5))
+ Results reproduced by [@mayankanand007](https://github.com/mayankanand007) on 2021-05-04 (commit [`906ca50`](https://github.com/castorini/anserini/commit/906ca5064cfe97266b92868e537e9372ac558e93))
+ Results reproduced by [@Albert-Ma](https://github.com/Albert-Ma) on 2021-05-07 (commit [`5bcbccd`](https://github.com/castorini/anserini/commit/5bcbccdb8e67a1c6a1a74da1219fd344c9e80b0b))
+ Results reproduced by [@rootofallevii](https://github.com/RootofalleviI) on 2021-05-14 (commit [`626da95`](https://github.com/castorini/anserini/commit/626da950249ecc1519c9b07710d1243e0653e1c5))
+ Results reproduced by [@jpark621](https://github.com/jpark621) on 2021-06-01 (commit [`2591e06`](https://github.com/castorini/anserini/commit/2591e063b4bee8881a641cf2167352ac212865a6))
+ Results reproduced by [@nimasadri11](https://github.com/nimasadri11) on 2021-06-27 (commit [`6f9352f`](https://github.com/castorini/anserini/commit/6f9352fc5d6a4938fadc2bda9d0c428056eec5f0))
+ Results reproduced by [@mzzchy](https://github.com/mzzchy) on 2021-07-05 (commit [`589928b`](https://github.com/castorini/anserini/commit/589928b1873b3ebe00234becd8b5da9d573dda6d))
+ Results reproduced by [@d1shs0ap](https://github.com/d1shs0ap) on 2021-07-16 (commit [`43ad899`](https://github.com/castorini/anserini/commit/43ad899337ac5e3b219d899bb218c4bcae18b1e6))
