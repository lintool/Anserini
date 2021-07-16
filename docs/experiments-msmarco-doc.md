# Anserini: BM25 Baselines for MS MARCO Document Ranking

This page contains instructions for running BM25 baselines on the [MS MARCO *document* ranking task](https://microsoft.github.io/msmarco/).
Note that there is a separate [MS MARCO *passage* ranking task](experiments-msmarco-passage.md).

**Setup Note:** If you're instantiating an Ubuntu VM on your system or on cloud (AWS and GCP), try to provision enough resources as the tasks such as building the index could take some time to finish such as RAM > 8GB and storage > 100 GB (SSD). This will prevent going back and fixing machine configuration again and again.

If you're a Waterloo undergraduate going through this guide as the [screening exercise](https://github.com/lintool/guide/blob/master/ura.md) of joining my research group, make sure you do the [passage ranking exercise](experiments-msmarco-passage.md) first.
Similarly, try to understand what you're actually doing, instead of simply [cargo culting](https://en.wikipedia.org/wiki/Cargo_cult_programming) (i.e., blinding copying and pasting commands into a shell).

## Data Prep

We're going to use the repository's root directory as the working directory.
First, we need to download and extract the MS MARCO document dataset:

```
mkdir collections/msmarco-doc

wget https://msmarco.blob.core.windows.net/msmarcoranking/msmarco-docs.trec.gz -P collections/msmarco-doc

# Alternative mirror:
# wget https://www.dropbox.com/s/w6caao3sfx9nluo/msmarco-docs.trec.gz -P collections/msmarco-doc
```

To confirm, `msmarco-docs.trec.gz` should have MD5 checksum of `d4863e4f342982b51b9a8fc668b2d0c0`.


## Indexing

There's no need to uncompress the file, as Anserini can directly index gzipped files.
Build the index with the following command:

```
sh target/appassembler/bin/IndexCollection -threads 1 -collection CleanTrecCollection \
 -generator DefaultLuceneDocumentGenerator -input collections/msmarco-doc \
 -index indexes/msmarco-doc/lucene-index-msmarco -storePositions -storeDocvectors -storeRaw
```

On a modern desktop with an SSD, indexing takes around 40 minutes.
There should be a total of 3,213,835 documents indexed.


## Retrieval

After indexing finishes, we can do a retrieval run.
The dev queries are already stored in our repo:

```
target/appassembler/bin/SearchCollection -topicreader TsvInt \
 -index indexes/msmarco-doc/lucene-index-msmarco \
 -topics src/main/resources/topics-and-qrels/topics.msmarco-doc.dev.txt \
 -output runs/run.msmarco-doc.dev.bm25.txt -bm25
```

On a modern desktop with an SSD, the run takes around 12 minutes.

After the run completes, we can evaluate with `trec_eval`:

```
$ tools/eval/trec_eval.9.0.4/trec_eval -c -mmap -mrecall.1000 src/main/resources/topics-and-qrels/qrels.msmarco-doc.dev.txt runs/run.msmarco-doc.dev.bm25.txt
map                   	all	0.2310
recall_1000           	all	0.8856
```

Let's compare to the baselines provided by Microsoft.
First, download:

```
wget https://msmarco.blob.core.windows.net/msmarcoranking/msmarco-docdev-top100.gz -P runs
gunzip runs/msmarco-docdev-top100.gz
```

Then, run `trec_eval` to compare.
Note that to be fair, we restrict evaluation to top 100 hits per topic (which is what Microsoft provides):

```
$ tools/eval/trec_eval.9.0.4/trec_eval -c -mmap -M 100 src/main/resources/topics-and-qrels/qrels.msmarco-doc.dev.txt runs/msmarco-docdev-top100
map                   	all	0.2219

$ tools/eval/trec_eval.9.0.4/trec_eval -c -mmap -M 100 src/main/resources/topics-and-qrels/qrels.msmarco-doc.dev.txt runs/run.msmarco-doc.dev.bm25.txt
map                   	all	0.2303
```

We see that "out of the box" Anserini is already better!

This dataset is part of the [MS MARCO Document Ranking Leaderboard](https://microsoft.github.io/MSMARCO-Document-Ranking-Submissions/leaderboard/).
Let's try to reproduce runs on there!

A few minor details to pay attention to: the official metric is MRR@100, so we want to only return the top 100 hits, and the submission files to the leaderboard have a slightly different format.
So, we use `SearchMsmarco` instead of `SearchCollection`:

```bash
sh target/appassembler/bin/SearchMsmarco -hits 100 -threads 1 \
 -index indexes/msmarco-doc/lucene-index-msmarco/ \
 -queries src/main/resources/topics-and-qrels/topics.msmarco-doc.dev.txt \
 -output runs/run.msmarco-doc.leaderboard-dev.bm25base.txt -k1 0.9 -b 0.4
```

The command above uses the default BM25 parameters (`k1=0.9`, `b=0.4`), and note we set `-hits 100`.
Command for evaluation:

```bash
$ python tools/scripts/msmarco/msmarco_doc_eval.py --judgments src/main/resources/topics-and-qrels/qrels.msmarco-doc.dev.txt --run runs/run.msmarco-doc.leaderboard-dev.bm25base.txt 
#####################
MRR @100: 0.23005723505603573
QueriesRanked: 5193
#####################
```

The above run corresponds to "Anserini's BM25, default parameters (k1=0.9, b=0.4)" on the leaderboard.

Here's the invocation for BM25 with parameters optimized for recall@100 (`k1=4.46`, `b=0.82`):

```bash
sh target/appassembler/bin/SearchMsmarco -hits 100 -threads 1 \
 -index indexes/msmarco-doc/lucene-index-msmarco/ \
 -queries src/main/resources/topics-and-qrels/topics.msmarco-doc.dev.txt \
 -output runs/run.msmarco-doc.leaderboard-dev.bm25tuned.txt -k1 4.46 -b 0.82
```

Command for evaluation:

```bash
$ python tools/scripts/msmarco/msmarco_doc_eval.py --judgments src/main/resources/topics-and-qrels/qrels.msmarco-doc.dev.txt --run runs/run.msmarco-doc.leaderboard-dev.bm25tuned.txt 
#####################
MRR @100: 0.2770296928568702
QueriesRanked: 5193
#####################
```

More details on tuning BM25 parameters below...

## BM25 Tuning

It is well known that BM25 parameter tuning is important.
The setting of `k1=0.9`, `b=0.4` is often used as a default.

Let's try to do better!
We tuned BM25 using the queries found [here](https://github.com/castorini/Anserini-data/tree/master/MSMARCO): these are five different sets of 10k samples from the training queries (using the `shuf` command).
The basic approach is grid search of parameter values in tenth increments.
We tuned on each individual set and then averaged parameter values across all five sets (this has the effect of regularization).
In separate trials, we optimized for:

+ recall@1000, since Anserini output serves as input to downstream rerankers (e.g., based on BERT), and we want to maximize the number of relevant documents the rerankers have to work with;
+ MRR@10, for the case where Anserini output is directly presented to users (i.e., no downstream reranking).

It turns out that optimizing for MRR@10 and MAP yields the same settings.

Here's the comparison between different parameter settings:

Setting                                                                | MRR@100 | MAP    | Recall@1000 |
:----------------------------------------------------------------------|--------:|-------:|------------:|
Default (`k1=0.9`, `b=0.4`)                                            | 0.2301  | 0.2310 | 0.8856 |
Optimized for MRR@100/MAP (`k1=3.8`, `b=0.87`)                         | 0.2784  | 0.2789 | 0.9326 |
Optimized for recall@100 (`k1=4.46`, `b=0.82`)                         | 0.2770  | 0.2775 | 0.9357 |

As expected, BM25 tuning makes a big difference!

Note that MRR@100 is computed with the leaderboard eval script (with 100 hits per query), while the other two metrics are computed with `trec_eval` (with 1000 hits per query).
So, we need to use different search programs, for example:

```
$ target/appassembler/bin/SearchCollection -topicreader TsvInt \
 -index indexes/msmarco-doc/lucene-index-msmarco \
 -topics src/main/resources/topics-and-qrels/topics.msmarco-doc.dev.txt \
 -output runs/run.msmarco-doc.dev.opt-mrr.txt -bm25 -bm25.k1 3.8 -bm25.b 0.87

$ tools/eval/trec_eval.9.0.4/trec_eval -c -mmap -mrecall.1000 src/main/resources/topics-and-qrels/qrels.msmarco-doc.dev.txt runs/run.msmarco-doc.dev.opt-mrr.txt
map                   	all	0.2789
recall_1000           	all	0.9326

$ sh target/appassembler/bin/SearchMsmarco -hits 100 -threads 1 \
 -index indexes/msmarco-doc/lucene-index-msmarco/ \
 -queries src/main/resources/topics-and-qrels/topics.msmarco-doc.dev.txt \
 -output runs/run.msmarco-doc.leaderboard-dev.opt-mrr.txt -k1 3.8 -b 0.87

$ python tools/scripts/msmarco/msmarco_doc_eval.py --judgments src/main/resources/topics-and-qrels/qrels.msmarco-doc.dev.txt --run runs/run.msmarco-doc.leaderboard-dev.opt-mrr.txt
#####################
MRR @100: 0.27836767424339787
QueriesRanked: 5193
#####################
```

That's it!

## Reproduction Log[*](reproducibility.md)

+ Results reproduced by [@edwinzhng](https://github.com/edwinzhng) on 2020-01-14 (commit [`3964169`](https://github.com/castorini/anserini/commit/3964169bf82a3783f9298907d9794f0bddf306f0))
+ Results reproduced by [@nikhilro](https://github.com/nikhilro) on 2020-01-21 (commit [`631589e`](https://github.com/castorini/anserini/commit/631589e9e08326373f46555e007e6c302c19126d))
+ Results reproduced by [@yuki617](https://github.com/yuki617) on 2020-03-29 (commit [`074723c`](https://github.com/castorini/anserini/commit/074723cbb10660fb9be2bfe6325739ab5fe0dd8d))
+ Results reproduced by [@HangCui0510](https://github.com/HangCui0510) on 2020-04-23 (commit [`0ae567d`](https://github.com/castorini/anserini/commit/0ae567df5c8a70ac211efd958c9ca1ff609ff782))
+ Results reproduced by [@x65han](https://github.com/x65han) on 2020-04-25 (commit [`f5496b9`](https://github.com/castorini/anserini/commit/f5496b905246084070f959e59626c6323210c3f2))
+ Results reproduced by [@y276lin](https://github.com/y276lin) on 2020-04-26 (commit [`8f48f8e`](https://github.com/castorini/anserini/commit/8f48f8e40a37e5f6b5910a3a3b5c050a0f9be914))
+ Results reproduced by [@stephaniewhoo](http://github.com/stephaniewhoo) on 2020-04-26 (commit [`8f48f8e`](https://github.com/castorini/anserini/commit/8f48f8e40a37e5f6b5910a3a3b5c050a0f9be914))
+ Results reproduced by [@YimingDou](https://github.com/YimingDou) on 2020-05-14 (commit [`3b0a642`](https://github.com/castorini/anserini/commit/3b0a6420e49863d9fe5908cf6e99582eb2d2882e))
+ Results reproduced by [@richard3983](https://github.com/richard3983) on 2020-05-14 (commit [`a65646f`](https://github.com/castorini/anserini/commit/a65646fe203bf5c9c32189a56082d6f4d3bc340d))
+ Results reproduced by [@MXueguang](https://github.com/MXueguang) on 2020-05-20 (commit [`3b2751e`](https://github.com/castorini/anserini/commit/3b2751e2d02a9d530e1c3d30b91083faeece8982))
+ Results reproduced by [@shaneding](https://github.com/shaneding) on 2020-05-23 (commit [`b6e0367`](https://github.com/castorini/anserini/commit/b6e0367ef4e2b4fce9d81c8397ef1188e35971e7))
+ Results reproduced by [@kelvin-jiang](https://github.com/kelvin-jiang) on 2020-05-24 (commit [`b6e0367`](https://github.com/castorini/anserini/commit/b6e0367ef4e2b4fce9d81c8397ef1188e35971e7))
+ Results reproduced by [@adamyy](https://github.com/adamyy) on 2020-05-28 (commit [`a1ecfa4`](https://github.com/castorini/anserini/commit/a1ecfa4aa38fb8a0cf41575d47629ba1c69228fb))
+ Results reproduced by [@TianchengY](https://github.com/TianchengY) on 2020-05-28 (commit [`2947a16`](https://github.com/castorini/anserini/commit/2947a1622efae35637b83e321aba8e6fccd43489))
+ Results reproduced by [@stariqmi](https://github.com/stariqmi) on 2020-05-28 (commit [`4914305`](https://github.com/castorini/anserini/commit/455169ea6a09f637817a6c4b4f6837dcc845f5f7))
+ Results reproduced by [@justinborromeo](https://github.com/justinborromeo) on 2020-06-11 (commit[`7954eab`](https://github.com/castorini/anserini/commit/7954eab43f17bb8d254987d5873933c0b9596bb4))
+ Results reproduced by [@yxzhu16](https://github.com/yxzhu16) on 2020-07-03 (commit [`68ace26`](https://github.com/castorini/anserini/commit/68ace26d0418a769df3d2b21e946495e54d462f6))
+ Results reproduced by [@LizzyZhang-tutu](https://github.com/LizzyZhang-tutu) on 2020-07-13 (commit [`8c98d5b`](https://github.com/castorini/anserini/commit/8c98d5ba0795bbea01bcef1e21abb153fe4c3da1))
+ Results reproduced by [@estella98](https://github.com/estella98) on 2020-08-05 (commit [`99092a8`](https://github.com/castorini/anserini/commit/99092a82179d7efd38fc0b8c7c967137a40cd96f))
+ Results reproduced by [@tangsaidi](https://github.com/tangsaidi) on 2020-08-19 (commit [`aba846`](https://github.com/castorini/anserini/commit/aba846aa07d6f319fb3dc9cb591c20b4ae69f9ef))
+ Results reproduced by [@qguo96](https://github.com/qguo96) on 2020-09-07 (commit [`e16b3c1`](https://github.com/castorini/anserini/commit/e16b3c160664057d4e00f2b4030cb6cb0d32fabd))
+ Results reproduced by [@yuxuan-ji](https://github.com/yuxuan-ji) on 2020-09-08 (commit [`0f9a8ec`](https://github.com/castorini/anserini/commit/0f9a8ec4f335fb49c9387351745d1c755afc0e84))
+ Results reproduced by [@wiltan-uw](https://github.com/wiltan-uw) on 2020-09-09 (commit [`93d913f`](https://github.com/castorini/anserini/commit/93d913f2619c44451be3d46816c7b9c44cbeb091))
+ Results reproduced by [@JeffreyCA](https://github.com/JeffreyCA) on 2020-09-13 (commit [`bc2628b`](https://github.com/castorini/anserini/commit/bc2628b9916ce42b8026497c695d4c4198547f04))
+ Results reproduced by [@jhuang265](https://github.com/jhuang265) on 2020-10-15 (commit [`66711b9`](https://github.com/castorini/anserini/commit/66711b9ff7722e2aea4ce2f59ca26ead5c091cac))
+ Results reproduced by [@rayyang29](https://github.com/rayyang29) on 2020-10-27 (commit [`ad8cc5a`](https://github.com/castorini/anserini/commit/ad8cc5a02a53f09a83a8a6bfd7d187c9c3f96bd5))
+ Results reproduced by [@Dahlia-Chehata](https://github.com/Dahlia-Chehata) on 2020-11-12 (commit [`22c0ad3`](https://github.com/castorini/anserini/commit/22c0ad3ebcff22c34a69ee3a7c122c4a9fb27a0e))
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
