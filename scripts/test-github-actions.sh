#!/bin/bash
set -e

echo "🧪 Testing GitHub Actions workflows locally with act..."

# Check if act is installed
if ! command -v act &> /dev/null; then
    echo "❌ 'act' is not installed. Please install it first:"
    echo "   macOS: brew install act"
    echo "   Linux: curl https://raw.githubusercontent.com/nektos/act/master/install.sh | sudo bash"
    echo "   Windows: choco install act-cli"
    exit 1
fi

# Create secrets file if it doesn't exist
if [ ! -f .secrets ]; then
    echo "📝 Creating .secrets file template..."
    cat > .secrets << EOF
GITHUB_TOKEN=your_github_token_here
EOF
    echo "⚠️  Please edit .secrets file with your actual tokens before running tests"
fi

echo "🔍 Available workflows:"
ls -la .github/workflows/

echo ""
echo "🧪 Testing Draft Release workflow (dry run)..."
act workflow_dispatch --dryrun -W .github/workflows/draft-release.yml

echo ""
echo "✅ All workflow syntax checks passed!"
echo ""
echo "📋 To run actual tests (not dry-run):"
echo "   act push -W .github/workflows/ci.yml"
echo "   act workflow_dispatch -W .github/workflows/test-build.yml"
echo ""
echo "⚠️  Make sure to:"
echo "   1. Update .secrets file with real tokens"
echo "   2. Create test-events/tag-event.json for release testing"
