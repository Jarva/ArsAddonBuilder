#!/bin/bash

find "projects" -type f | while read -r file; do
    module=$(jq -c "." $file)
    repo=$(echo "$module" | jq -cr ".repo")
    branch=$(echo "$module" | jq -cr ".branch")
    current=$(echo "$module" | jq -cr ".hash")
    head=$(git ls-remote "https://github.com/$repo.git" "refs/heads/$branch" | cut -f 1)
    if [ "$current" != "$head" ]; then
        echo "Updating $file..."
        echo "$module" | jq '.hash = $hash' --arg hash "$head" > $file
    fi
done
