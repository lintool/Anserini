import json
import xmltodict
import os

input_path = os.path.join(args.collection_path)
output_path = os.path.join(args.output_folder)

with open(output_path + "output.json", 'w') as out_file:
    out_file.write('[\n')
    for xml_file in os.listdir(input_path + 'datasets_xml'):
        print(xml_file)
        with open(os.path.join('datasets_xml', xml_file), 'r', encoding='utf8') as f:
            xml_string = f.read()

        json_string = json.dumps(xmltodict.parse(xml_string), indent=4)

        out_file.write(json_string)
        out_file.write(',')

    out_file.write('\n]')

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Converts iso19115 xml files to json files.')
    parser.add_argument('--collection_path', required=True, help='iso19115 collection file')
    parser.add_argument('--output_folder', required=True, help='output folder')
    # not used yet since dataset is very small
    parser.add_argument('--max_docs_per_file', default=1000000, type=int, help='maximum number of documents in each jsonl file.')

    args = parser.parse_args()

    if not os.path.exists(args.output_folder):
        os.makedirs(args.output_folder)

    convert_collection(args)
    print('Done!')

