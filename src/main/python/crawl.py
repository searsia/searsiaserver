
import json
import math
import sys
import time
import re
import requests

SLEEP = 1

if len(sys.argv) != 2:
    print('Usage: crawl.py <searsia-template>', file=sys.stderr)
    sys.exit()

template = sys.argv[1]

todo = []
done = []
hits = []


def do_request(template, engine='index', page=1):
    url = template.replace('/index', '/' + engine)
    url = re.sub(r'{startPage\??}', str(page), url)
    url = re.sub(r'{[^}]+}', '', url)
    try:
        response = requests.get(url)
        time.sleep(SLEEP)
        return response.json()
    except Exception as exception:
        print(exception, file=sys.stderr)
        sys.exit()


def save_engine(name, result):
    file_name = name + '.json'
    with open(file_name, 'w') as fd:
        fd.write(json.dumps(result, indent=2, sort_keys=True))


print('Crawling... please, wait.')

for page in range(99):
    new_found = False
    result = do_request(template, 'index', page + 1)
    print('Page', page + 1)
    if 'hits' in result:
        for hit in result['hits']:
            if 'rid' in hit:
                if hit in hits:
                    print('Warning: duplicate. Check template startPage.',
                          file=sys.stderr)
                    break
                hits.append(hit)
                new_found = True
    if not new_found:
        break

result['hits'] = hits
save_engine('index', result)
print('Ok: index')

if not hits:
    print('Error: No hits found.', file=sys.stderr)
    sys.exit()

for hit in hits:
    engine = hit['rid']
    result = do_request(template, engine)
    save_engine(engine, result)
    uptime = math.nan
    if 'health' in result:
        health = result['health']
        if ('lastsuccess' in health and 'lasterror' in health and
                health['lastsuccess'] > health['lasterror']):
            conclusion = 'Ok'
        else:
            conclusion = 'Error'
        if 'requestserr' in health and 'requestsok' in health:
            requestserr = int(health['requestserr'])
            requestsok = int(health['requestsok'])
            uptime = 100 * requestsok / (requestsok + requestserr + 0.01)
    else:
        conclusion = 'No health'
    print(conclusion + ': ' + engine + ', ' + '{:2.1f}'.format(uptime) + '% uptime')
