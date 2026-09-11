/**
 * Replaces all occurrences of ${KGraphQLVersion} in the HTML with the latest version tag provided by Material for MkDocs
 * in the .md-source__fact--version element.
 */
document$.subscribe(function () {
    let toReplace = '${KGraphQLVersion}';
    let latest_tag = document.querySelector("li.md-source__fact--version")?.textContent;
    if (latest_tag != null) {
        const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, {
            acceptNode: function (node) {
                return node.textContent.includes(toReplace)
                    ? NodeFilter.FILTER_ACCEPT
                    : NodeFilter.FILTER_REJECT;
            }
        });
        let node;
        while ((node = walker.nextNode()) !== null) {
            node.nodeValue = node.nodeValue.replaceAll(toReplace, latest_tag);
        }
    }
})
