
## Tasks

- [ ] Change the `manager` in Android, and `window.chrome.webview.postMessage` to be a common function call (define in `default.js`).
- [ ] Add dark/light mode support to Windows side
- [ ] Fully port the Android pages over to Windows
- [ ] Change `views` to `pages` on Android side
- [ ] Restore permissions to Windows side
- [ ] Add demos of video playing, and camera capture to both sides

Calling standard manager:
```javascript
    HandlePageRequest({
        action: "tester-and-batch",
        parameters: {testerName: testerName, batchId: batchId}
    });
```