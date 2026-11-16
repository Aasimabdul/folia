#!/bin/bash
# Folia - GitHub Repository Setup Script
set -e

echo "🌿 Setting up GitHub repository for Folia..."

# 1. Initialize git if not already initialized
if [ ! -d ".git" ]; then
    echo "Initializing local Git repository..."
    git init -b main
else
    echo "Git repository already initialized."
fi

# 2. Add all files according to .gitignore
echo "Staging files..."
git add .

# 3. Create initial commit
echo "Creating initial commit..."
git commit -m "Initial commit: Folia - Modern All-Format Document Reader for Android" || echo "Nothing new to commit."

# 4. Prompt for GitHub repository if not provided
if [ -z "$1" ]; then
    echo ""
    echo "To connect to your GitHub account:"
    echo "1. Create a new repository named 'folia' on https://github.com/new"
    echo "2. Run this script with your GitHub username or remote URL:"
    echo "   ./setup_github.sh <your-github-username>"
    echo "   OR"
    echo "   git remote add origin https://github.com/<your-username>/folia.git"
    echo "   git push -u origin main"
    exit 0
fi

USERNAME=$1
REPO_URL="https://github.com/${USERNAME}/folia.git"

echo "Setting remote origin to $REPO_URL..."
git remote remove origin 2>/dev/null || true
git remote add origin "$REPO_URL"

echo "Pushing main branch to $REPO_URL..."
git push -u origin main

echo "✅ Folia is now successfully pushed to GitHub!"
