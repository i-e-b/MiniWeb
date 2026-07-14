const _base64 = [
    "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
    "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z",
    "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
    "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z",
    "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "+", "/"
];

function toBase64(bytes) {
    let result = '', i, l = bytes.length;
    for (i = 2; i < l; i += 3) {
        result += _base64[bytes[i - 2] >> 2];
        result += _base64[((bytes[i - 2] & 0x03) << 4) | (bytes[i - 1] >> 4)];
        result += _base64[((bytes[i - 1] & 0x0F) << 2) | (bytes[i] >> 6)];
        result += _base64[bytes[i] & 0x3F];
    }
    if (i === l + 1) {
        result += _base64[bytes[i - 2] >> 2];
        result += _base64[(bytes[i - 2] & 0x03) << 4];
        result += "==";
    }
    if (i === l) {
        result += _base64[bytes[i - 2] >> 2];
        result += _base64[((bytes[i - 2] & 0x03) << 4) | (bytes[i - 1] >> 4)];
        result += _base64[(bytes[i - 1] & 0x0F) << 2];
        result += "=";
    }
    return result;
}



async function openCamera (prefix) {
    const video = document.getElementById(prefix+'CamPreview');
    const canvas = window.canvas = document.getElementById(prefix+'CamSnapshot');

    const camTriggerBtn = document.getElementById(prefix+'CamTrigger');
    const camStartBtn = document.getElementById(prefix+'CamStart');

    navigator.getMedia = navigator.getUserMedia || navigator.webkitGetUserMedia || navigator.mozGetUserMedia || navigator.msGetUserMedia
    await navigator.mediaDevices.getUserMedia({
        audio: false,
        video: {
            frameRate: {
                min: 15,
                ideal: 30,
                max: 60
            },
            facingMode: 'user'
        }
    }).then(function (stream) {
        document.getElementById(prefix).value = ""; // Reset existing value

        unhide(video);
        hide(canvas);

        unhide(camTriggerBtn);
        hide(camStartBtn);

        window.stream = stream // make stream available to browser console
        video.srcObject = stream
    }).catch(function (err) {
        alert(err)
    })
}

function scanCamera(prefix) {
    const video = document.getElementById(prefix+'CamPreview');
    const canvas = window.canvas = document.getElementById(prefix+'CamSnapshot');

    const camTriggerBtn = document.getElementById(prefix+'CamTrigger');
    const camStartBtn = document.getElementById(prefix+'CamStart');
    const spinner = document.getElementById(prefix+'ScanSpinner');

    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;

    unhide(spinner);

    let ctx = canvas.getContext('2d', { willReadFrequently: true });
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);

    // Send the image data back to the C# code.
    // If the C# code finds a QR code, it will send a message back to the page.
    // noinspection JSCheckFunctionSignatures
    const data = ctx.getImageData(0, 0, canvas.width, canvas.height, {pixelFormat:"rgba-unorm8"}).data;
    const test = toBase64(data);
    window.chrome.webview.postMessage({
        action: "camera", data: test,
        parameters: {target: prefix, width: canvas.width, height: canvas.height}
    });

    // Scale the result down
    canvas.width = 320;
    canvas.height = 240;
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);

    // Hide the video source, and disconnect the stream
    unhide(canvas);
    hide(video);

    unhide(camStartBtn);
    hide(camTriggerBtn);

    window.stream = null;
    video.srcObject = null;
}

// noinspection JSUnusedGlobalSymbols
function setInnerHtml(target, valueBase64, centre){
    const value = atob(valueBase64);
    const elem = document.getElementById(target);
    elem.innerHTML = value;

    if (centre) {
        // Try to scroll next step into view
        elem.scrollIntoView({
            behavior: 'auto',
            block: 'center',
            inline: 'center'
        });
    }
}

function hide(elem){
    try {
        if (elem.classList) {
            elem.classList.add("hide");
        } else {
            document.getElementById(elem).classList.add("hide");
        }
    } catch (e) {
        console.log(e);
    }
}

function unhide(elem){
    try {
        if (elem.classList) {
            elem.classList.remove("hide");
        } else {
            document.getElementById(elem).classList.remove("hide");
        }
    } catch (e) {
        console.log(e);
    }
}

function toggleHide(elem){
    try {
        if (!elem.classList) {
            elem = document.getElementById(elem);
        }
        if (elem.classList.contains("hide")) elem.classList.remove("hide");
        else elem.classList.add("hide");
    } catch (e) {
        console.log(e);
    }
}

/* Set inner text on `id`, and show it for `time` seconds. */
function showToast(id, time, text){
    const target = document.getElementById(id);
    if (!target) return;

    target.innerText = text;
    target.time = time;
    unhide(target);
}

function toastTimer(){
    let toasts = document.getElementsByClassName("pop-toast");
    for (const toast of toasts) {
        if (!toast.time) continue;

        toast.time = (0|toast.time) - 1;

        if (toast.time <= 0) {
            hide(toast);
            delete toast.time;
        }
    }
}
setInterval(toastTimer, 1000);

function onPrint(){
    window.chrome.webview.postMessage({"action":"print"});
}

function centre(elem) {
    if (!elem.scrollIntoView) {
        elem = document.getElementById(elem);
    }

    elem.scrollIntoView({
        behavior: 'auto',
        block: 'center',
        inline: 'center'
    });
}

function help(id){
    let target = document.getElementById(id);
    target.style.display = "block";
    target.addEventListener("click", closeHelp);
}

function closeHelp(e){
    if (!e) return;
    if (!e.target.classList.contains('close') && !e.target.classList.contains('modal')) return;
    e.stopPropagation();
    console.log("Closing from "+e.target.classList);

    let targets = document.getElementsByClassName('modal');
    for (const target of targets) {
        target.style.display = "none";
    }
    return true;
}

function submitFormTo(formId, url){
    const form = document.getElementById(formId);
    form.action = url;
    form.submit();
}