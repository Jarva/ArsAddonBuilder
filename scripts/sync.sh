#!/bin/bash

find "projects" -type f | while read -r file; do
    module=$(jq -c "." $file)
    url=$(echo "$module" | jq -cr ".url")
    branch=$(echo "$module" | jq -cr ".branch")
    current=$(echo "$module" | jq -cr ".hash")
    head=$(git ls-remote "$url" "refs/heads/$branch" | cut -f 1)
    if [ "$current" != "$head" ]; then
        echo "Updating $file..."
        echo "$module" | jq '.hash = $hash' --arg hash "$head" > $file
    fi
done
