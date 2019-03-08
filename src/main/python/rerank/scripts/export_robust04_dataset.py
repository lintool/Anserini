'''scripts to generate the Robust04 dataset from the Anserini runs for reranking purpose

Run BM25/BM25+RM3 on TREC Robust04 and store the output for reranking.
Makes an external call to trec_eval for the actual computation of metrics.
'''


import json
import os
from tqdm import tqdm
import string
import argparse

from utils import *

parser = argparse.ArgumentParser()
parser.add_argument('--method', default='BM25', help='[BM25, BM25+RM3]')
parser.add_argument('--output_dir', default='src/main/python/rerank/data', help='output directory')
parser.add_argument('--K', default=1000, type=int, help='number of document retrieved')
parser.add_argument('--index', default="/tuna1/indexes/lucene-index.robust04.pos+docvectors+rawdocs", help='index of Robust04 corpus')
args = parser.parse_args()

searcher = JSearcher(JString(args.index))

fqrel = "src/main/resources/topics-and-qrels/qrels.robust2004.txt"
best_rm3_parameters = [[47, 9, 0.3], [47, 9, 0.3], [47, 9, 0.3], [47, 9, 0.3], [26, 8, 0.3]]
for split in range(1, 6):
    ftrain = json.load(open("src/main/resources/fine_tuning/drr_folds/rob04.train.s{}.json".format(split)))
    fdev = json.load(open("src/main/resources/fine_tuning/drr_folds/rob04.dev.s{}.json".format(split)))
    ftest = json.load(open("src/main/resources/fine_tuning/drr_folds/rob04.test.s{}.json".format(split)))
    for mode, data in [("train", ftrain), ("dev", fdev), ("test", ftest)]: #  
        qid2text = get_qid2text_new(data)
        method = "BM25_0.9_0.5_RM3_{}_{}_{}".format(*best_rm3_parameters[split-1])
        prediction_fn = "predictions_tuned/predict_{}_robust04_split{}_{}.txt".format(method, split, mode)
        output_fn = os.path.join("Robust04Corpus/split{}_{}_{}.txt".format(split, mode, method))
        if not os.path.exists(output_fn):
            os.makedirs(output_fn)
            searcher.setBM25Similarity(0.9, 0.5)
        if args.method == "BM25+RM3":
                searcher.setRM3Reranker(*best_rm3_parameters[split-1])
        elif args.method == "BM25":
            searcher.setDefaultReranker()
        else:
            print("Unsupported ranking method")
            break 
        search_robust04(searcher, prediction_fn, qid2text, output_fn, qid2reldocids, K=args.K)
        calculate_score(prediction=prediction_fn)
