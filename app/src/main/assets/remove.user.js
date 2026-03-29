// ==UserScript==
// @name         open the link directly
// @namespace    http://tampermonkey.net/
// @version      0.2.0
// @description  移除外链跳转页面
// @author       nediiii (Refactored)
// @match        *://*.csdn.net/*
// @match        *://*.gitee.com/*
// @match        *://*.jianshu.com/*
// @match        *://*.juejin.cn/*
// @match        *://*.zhihu.com/*
// @match        *://*.leetcode.cn/*
// @match        *://*.leetcode-cn.com/*
// @run-at       document-start
// @license      GPLv3 License
// @grant        unsafeWindow
// ==/UserScript==

(function () {
    'use strict';

    // 保存并重写原始 open 方法
    unsafeWindow.__otld_open = unsafeWindow.open;
    var myopen = function (url, name, features) {
        return unsafeWindow.__otld_open(url, name, features);
    }
    var _myopen = myopen.bind(null);
    _myopen.toString = unsafeWindow.__otld_open.toString;
    Object.defineProperty(unsafeWindow, 'open', {
        value: _myopen
    });

    const getValidURL = (url) => {
        try {
            return new URL(url);
        } catch (error) {
            return getValidURLWithBase(url);
        }
    }

    const getValidURLWithBase = (url) => {
        try {
            return new URL(url, window.location.origin);
        } catch (error) {
            return null;
        }
    }

    const isHttpProtocol = (url) => {
        return url.protocol === 'http:' || url.protocol === 'https:';
    }

    // 仅保留要求的 6 个站点的匹配规则
    const patter_match = {
        zhihu: { pattern: /https?:\/\/link\.zhihu\.com\/?\?target=(.+)$/ },
        jianshu2: { pattern: /https?:\/\/links\.jianshu\.com\/go\?to=(.+)$/ },
        jianshu3: { pattern: /https?:\/\/link\.jianshu\.com\/\?t=(.+)$/ },
        jianshu4: { pattern: /https?:\/\/www\.jianshu\.com\/go-wild\?ac=2&url=(.+)$/ },
        juejin: { pattern: /https?:\/\/link\.juejin\.cn\/?\?target=(.+)$/ },
        gitee: { pattern: /https?:\/\/gitee\.com\/link\?target=(.+)$/ },
        leetcodecn: { pattern: /https?:\/\/leetcode-cn\.com\/link\/\?target=(.+)$/ },
        leetcodecn2: { pattern: /https?:\/\/leetcode\.cn\/link\/\?target=(.+)$/ },
        csdn: { host: 'csdn.net' },
        csdn2: { pattern: /https?:\/\/link\.csdn\.net\/\?target=(.+)$/ },
    }

    const matchHostResolver = (url, host) => {
        if (!window.location.host.includes(host)) {
            return false;
        }
        if (url.host.includes(host)) {
            return false;
        }
        return true;
    }

    const getMatchPattern = (url) => {
        for (let i in patter_match) {
            if (patter_match[i].hasOwnProperty('host') && matchHostResolver(url, patter_match[i].host)) {
                return patter_match[i];
            }
            if (patter_match[i].hasOwnProperty('pattern') && url.href.match(patter_match[i].pattern)) {
                return patter_match[i];
            }
        }
        return null;
    }

    // 纯本地正则解析，移除异步
    const patternResolve = (matchPattern, href) => {
        if (matchPattern.hasOwnProperty('pattern')) {
            const matcher = href.match(matchPattern.pattern);
            if (!matcher) { return href; }
            const encodeURI = matcher[1];
            return decodeURIComponent(encodeURI);
        }
        return href;
    }

    const getAnchorElement = (e) => {
        let target = e.target;
        while (target) {
            if (target.tagName.toLowerCase() === 'a' && target.hasAttribute('href')) {
                return target;
            }
            target = target.parentElement;
        }
        return null;
    }

    // 主体函数 (已同步化)
    const otld = (link, e, handleEventAfterMatch, handleRealURI, errCallback) => {
        let url = getValidURL(link);
        if (url === null || !isHttpProtocol(url)) {
            return;
        }

        const matchPattern = getMatchPattern(url);
        if (!matchPattern) {
            return;
        }

        handleEventAfterMatch && handleEventAfterMatch(e);

        const target = (() => {
            if (matchPattern === patter_match.csdn) {
                return '_blank';
            }
            if (!e || !getAnchorElement(e)) {
                return '_self';
            }
            let anchor = getAnchorElement(e);
            if (anchor.hasAttribute('target')) {
                return anchor.getAttribute('target');
            }
            return '_self';
        })();

        try {
            const realURI = patternResolve(matchPattern, url.href);
            handleRealURI(realURI, target);
        } catch (error) {
            errCallback && errCallback(target);
        }
    }

    // 监听点击事件
    document.addEventListener('click', (e) => {
        let anchor = getAnchorElement(e);
        if (!anchor) return;

        let href = anchor.getAttribute('href');
        if (!href) return;

        const stopEvent = (e) => {
            if (!e) return;
            e.preventDefault();
            e.stopPropagation();
        }

        otld(
            href, 
            e, 
            stopEvent, 
            (realURI, target) => { window.open(realURI, target) }, 
            (target) => { window.open(href, target); }
        );

    }, { capture: true });

    // 处理手动输入或者鼠标拖动的链接
    otld(
        window.location.href, 
        null, 
        null, 
        (realURI, target) => { window.location.replace(realURI); }, 
        null
    );

})();