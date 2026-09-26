import unittest
from generate_release_notes import render_notes

REPO = 'owner/app'
USER = {'login': 'Alice', 'html_url': 'https://github.com/Alice'}
PR = {'number': 12, 'title': 'feat(nav)!: Add navigation', 'user': USER,
      'html_url': 'https://github.com/owner/app/pull/12', 'merged_at': '2026-09-26',
      'base': {'repo': {'full_name': REPO}}, 'merge_commit_sha': 'abc1234'}


class ReleaseNotesTest(unittest.TestCase):
    def test_pr_group_credit_and_deduplication(self):
        notes = render_notes([('abc1234', 'first', 'A'), ('def5678', 'second', 'A')],
                             REPO, lambda endpoint: [PR])
        self.assertIn("## What's Changed\n\n### New Features", notes)
        self.assertEqual(notes.count('in [#12]'), 1)
        self.assertIn('by @Alice in [#12]', notes)
        self.assertNotIn('## Contributors', notes)
        self.assertNotIn('[**@Alice**]', notes)

    def test_direct_commit_missing_account_and_unmerged_pr(self):
        def api(endpoint):
            if '/pulls?' in endpoint:
                return [dict(PR, merged_at=None)]
            return {'author': None, 'html_url': 'https://github.com/owner/app/commit/abc1234'}
        notes = render_notes([('abc1234', 'fix: repair [link]', 'Local Author')], REPO, api)
        self.assertIn('### Fixes', notes)
        self.assertIn('by Local Author in [abc1234]', notes)
        self.assertIn('repair \\[link\\]', notes)
        self.assertNotIn('## Contributors', notes)

    def test_merged_pr_preferred_and_foreign_pr_ignored(self):
        foreign = dict(PR, number=1, base={'repo': {'full_name': 'other/app'}})
        older = dict(PR, number=2, merge_commit_sha='other')
        notes = render_notes([('abc1234', 'first', 'A')], REPO,
                             lambda endpoint: [foreign, older, PR])
        self.assertIn('in [#12]', notes)
        self.assertNotIn('in [#2]', notes)

    def test_release_commits_and_prs_are_omitted(self):
        def unexpected_api(endpoint):
            self.fail('Release commit should be skipped before API access')
        self.assertEqual(render_notes([('abc', '[release]26.9', 'A')], REPO,
                                      unexpected_api), "## What's Changed\n")
        notes = render_notes([('abc', 'prepare version', 'A')], REPO,
                             lambda endpoint: [dict(PR, title='[release]26.9')])
        self.assertEqual(notes, "## What's Changed\n")

    def test_empty_and_api_failure(self):
        self.assertEqual(render_notes([], REPO), "## What's Changed\n")
        def fail(endpoint):
            raise RuntimeError('API unavailable')
        with self.assertRaises(RuntimeError):
            render_notes([('abc1234', 'feat: add', 'A')], REPO, fail)


if __name__ == '__main__':
    unittest.main()
