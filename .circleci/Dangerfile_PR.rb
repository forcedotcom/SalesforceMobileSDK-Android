#!/usr/bin/env ruby

# Warn when there is a big PR
warn("Big PR, try to keep changes smaller if you can.", sticky: true) if git.lines_of_code > 500

# Stop skipping some manual testing
warn("📱 Needs testing on a real device if change is non-trivial.") if git.lines_of_code > 50

# Mainly to encourage writing up some reasoning about the PR, rather than
# just leaving a title
if github.pr_body.length < 3
  warn("Please provide a summary in the Pull Request description", sticky: true)
end

# Make it more obvious that a PR is a work in progress and shouldn't be merged yet.
has_wip_label = github.pr_labels.any? { |label| label.include? "WIP" }
has_wip_title = github.pr_title.include? "[WIP]"
has_dnm_label = github.pr_labels.any? { |label| label.include? "DO NOT MERGE" }
has_dnm_title = github.pr_title.include? "[DO NOT MERGE]"
if has_wip_label || has_wip_title
  warn("PR is classed as Work in Progress", sticky: true)
end
if has_dnm_label || has_dnm_title
  warn("At the authors request please DO NOT MERGE this PR", sticky: true)
end

fail("Please re-submit this PR to the dev branch, we may have already fixed your issue.", sticky: true) if github.branch_for_base != "dev"