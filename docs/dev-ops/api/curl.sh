curl http://49.234.8.125:11434/api/generate \
  -H "Content-Type: application/json" \
  -d '{
        "model": "deepseek-r1:1.5b",
        "prompt": "你是谁",
        "stream": false
      }'