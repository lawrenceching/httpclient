# Istio Issue  36699

This is the PoC of https://github.com/istio/istio/issues/36699

### Usage

```bash
java -jar httpclient.jar --url=http://fortio:8080 --qps=1 --http2-prior-knowledge=true
java -jar httpclient.jar --url=http://h2c --proxy=fortio:8888 --qps=100 --http2-prior-knowledge=true
```

