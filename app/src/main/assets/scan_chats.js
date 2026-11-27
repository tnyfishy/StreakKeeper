// Complete High-Speed TikTok Inbox Chat Scanner
// - Scans 100% of virtualized + paginated chats (deep scrollable detection, 60% overlap,
//   IntersectionObserver bounce, fiber-key signatures, and retry-safe header reading)
// - Strictly extracts real TikTok streak days & lit-today state
// - Extracts avatarUrl, username, displayName, isGroup, plus logged-in user profile

(function () {
    var maxScrollAttempts = 300;
    var maxChatsToScan = 1000;
    var scrollAttempts = 0;
    var staleBottomRetries = 0;
    var maxStaleBottomRetries = 6;

    var chatItems = [];
    var chatIndex = 0;
    var seenKeys = {};
    var seenRowSigs = {};
    var failedRowAttempts = {};
    var discoveredCount = 0;
    var newFoundInPass = 0;
    var finished = false;
    var lastHeaderSignature = '';
    var savedScrollTop = 0;
    var diagDumped = false;
    var stats = { link: 0, fiber: 0, click: 0, clickMiss: 0, store: 0 };

    var reportLog = function (msg) {
        if (typeof ScannerApp !== 'undefined') ScannerApp.log(msg);
    };

    var reportProgress = function (status) {
        if (typeof ScannerApp !== 'undefined') ScannerApp.onProgress(discoveredCount, status);
    };

    var reportChat = function (username, displayName, isGroup, avatarUrl, streakDays, streakLitToday) {
        var cleanUser = (username || '').replace(/^@/, '').trim();
        var cleanDisp = (displayName || '').trim();
        var key = isGroup ? ('g:' + cleanDisp.toLowerCase()) : ('u:' + cleanUser.toLowerCase());
        if (!key || key === 'g:' || key === 'u:') return false;
        if (seenKeys[key]) return false;
        seenKeys[key] = true;
        discoveredCount++;
        newFoundInPass++;
        if (typeof ScannerApp !== 'undefined') {
            ScannerApp.onChatFound(
                cleanUser,
                cleanDisp || cleanUser,
                !!isGroup,
                avatarUrl || '',
                streakDays > 0 ? Math.floor(streakDays) : 0,
                !!streakLitToday
            );
        }
        return true;
    };

    var reportDone = function (err) {
        if (finished) return;
        finished = true;
        reportLog(
            '[SCAN-DONE] total=' + discoveredCount +
            ' scrolls=' + scrollAttempts +
            ' link=' + stats.link +
            ' fiber=' + stats.fiber +
            ' click=' + stats.click +
            ' miss=' + stats.clickMiss +
            ' store=' + stats.store +
            (err ? ' err=' + err : '')
        );
        if (typeof ScannerApp !== 'undefined') {
            ScannerApp.onScanComplete(discoveredCount, err || '');
        }
    };

    // 1. Extract logged-in user's own TikTok Display Name & Avatar
    var extractOwnProfile = function () {
        var nick = '';
        var avatar = '';
        try {
            var rehydration = document.getElementById('__UNIVERSAL_DATA_FOR_REHYDRATION__');
            if (rehydration && rehydration.textContent) {
                var parsed = JSON.parse(rehydration.textContent);
                var scope = parsed && parsed['__DEFAULT_SCOPE__'];
                var user = scope && scope['webapp.app-context'] && scope['webapp.app-context'].user;
                if (user) {
                    nick = user.nickName || user.nickname || '';
                    if (Array.isArray(user.avatarUri) && user.avatarUri.length > 0) avatar = user.avatarUri[0];
                    else avatar = user.avatarThumb || user.avatarMedium || '';
                }
            }
        } catch (e) { }

        if ((nick || avatar) && typeof ScannerApp !== 'undefined') {
            ScannerApp.onAccountProfile(String(nick || ''), String(avatar || ''));
        }
    };

    // 2. Helpers: Avatar & STRICT Streak Extraction
    var extractAvatarFromElement = function (el) {
        if (!el) return '';
        var imgs = el.querySelectorAll('img');
        for (var i = 0; i < imgs.length; i++) {
            var src = imgs[i].currentSrc || imgs[i].getAttribute('src') || '';
            if (src && src.indexOf('http') === 0 && src.indexOf('streak') === -1 && src.indexOf('emoji') === -1 && src.indexOf('fire') === -1) {
                var w = imgs[i].naturalWidth || imgs[i].width || 32;
                if (w >= 20) return src;
            }
        }
        return '';
    };

    var isWarmFlameColor = function (colorStr) {
        if (!colorStr) return false;
        var s = colorStr.toLowerCase();
        if (s.indexOf('#fe2c55') !== -1 || s.indexOf('#ff') !== -1 || s.indexOf('#f9') !== -1 || s.indexOf('#fa') !== -1 || s.indexOf('orange') !== -1) return true;
        var m = s.match(/rgba?\(\s*(\d+),\s*(\d+),\s*(\d+)/);
        if (m) {
            var r = parseInt(m[1], 10), g = parseInt(m[2], 10), b = parseInt(m[3], 10);
            if (r >= 200 && (r - b) >= 65 && g <= 185) return true;
        }
        return false;
    };

    var isUnreadOrTimeEl = function (el) {
        if (!el) return true;
        var cls = (el.className || '').toString().toLowerCase();
        var e2e = (el.getAttribute && el.getAttribute('data-e2e') || '').toLowerCase();
        if (cls.indexOf('unread') !== -1 || cls.indexOf('badge') !== -1 || cls.indexOf('time') !== -1 || cls.indexOf('dot') !== -1) return true;
        if (e2e.indexOf('unread') !== -1 || e2e.indexOf('time') !== -1) return true;
        return false;
    };

    var extractStreakFromDom = function (root) {
        var result = { days: 0, litToday: false };
        if (!root) return result;

        var streakEls = root.querySelectorAll(
            '[data-e2e*="streak" i], [class*="streak" i], [class*="Streak"], [class*="flame" i], [class*="Flame"], [aria-label*="streak" i], [aria-label*="chuỗi" i]'
        );
        for (var i = 0; i < streakEls.length; i++) {
            var el = streakEls[i];
            if (isUnreadOrTimeEl(el)) continue;
            var txt = (el.textContent || '').trim();
            if (/\d+:\d+/.test(txt)) continue;
            var numMatch = txt.match(/^\D*(\d{1,4})\D*$/);
            if (numMatch) {
                var days = parseInt(numMatch[1], 10);
                if (days > 0 && days < 10000) {
                    result.days = days;
                    var cls = (el.className || '').toString().toLowerCase();
                    var html = (el.innerHTML || '').toLowerCase();
                    var styleColor = '';
                    try { styleColor = window.getComputedStyle(el).color || ''; } catch (e) { }
                    var isInactive = cls.indexOf('gray') !== -1 || cls.indexOf('grey') !== -1 ||
                        cls.indexOf('inactive') !== -1 || cls.indexOf('expire') !== -1 ||
                        cls.indexOf('dim') !== -1 || cls.indexOf('pending') !== -1 ||
                        cls.indexOf('freeze') !== -1 ||
                        txt.indexOf('\u23F3') !== -1 || html.indexOf('hourglass') !== -1;
                    result.litToday = !isInactive && (
                        cls.indexOf('active') !== -1 || cls.indexOf('lit') !== -1 ||
                        isWarmFlameColor(styleColor) || isWarmFlameColor(html)
                    );
                    return result;
                }
            }
        }

        var imgs = root.querySelectorAll('img');
        for (var m = 0; m < imgs.length; m++) {
            var src = (imgs[m].getAttribute('src') || '').toLowerCase();
            var alt = (imgs[m].getAttribute('alt') || '').toLowerCase();
            if (src.indexOf('streak') !== -1 || src.indexOf('flame') !== -1 || alt.indexOf('streak') !== -1 || alt.indexOf('chuỗi') !== -1) {
                var p = imgs[m].parentElement;
                if (p && !isUnreadOrTimeEl(p)) {
                    var pTxt = (p.textContent || '').trim();
                    if (/^\d{1,4}$/.test(pTxt)) {
                        var dNum = parseInt(pTxt, 10);
                        if (dNum > 0 && dNum < 10000) {
                            result.days = dNum;
                            result.litToday = src.indexOf('gray') === -1 && src.indexOf('grey') === -1 && src.indexOf('inactive') === -1;
                            return result;
                        }
                    }
                }
            }
        }

        // Never regex raw innerText for 🔥 + digits: message previews like "🔥🔥🔥\n10:44"
        // or usernames with emojis falsely match the timestamp hour ("10") as streak days!
        return result;
    };

    var extractStreakFromObj = function (obj) {
        var res = { days: 0, litToday: false };
        if (!obj || typeof obj !== 'object') return res;

        var directDays = obj.streakCount || obj.streak_count || obj.streakDays || obj.streak_days || obj.consecutiveDays;
        if (typeof directDays === 'number' && directDays > 0 && directDays < 10000) {
            res.days = directDays;
        } else if (typeof directDays === 'string' && /^\d{1,4}$/.test(directDays)) {
            res.days = parseInt(directDays, 10);
        }

        var streakObj = obj.streak || obj.streakInfo || obj.streak_info || obj.petStreak || obj.streakData;
        if (streakObj && typeof streakObj === 'object') {
            var subDays = streakObj.streakCount || streakObj.streak_count || streakObj.streakDays ||
                streakObj.streak_days || streakObj.days || streakObj.count || streakObj.level;
            if (typeof subDays === 'number' && subDays > 0 && subDays < 10000) res.days = subDays;
            else if (typeof subDays === 'string' && /^\d{1,4}$/.test(subDays)) res.days = parseInt(subDays, 10);

            if (res.days > 0) {
                if (streakObj.isLitToday === true || streakObj.hasSentToday === true ||
                    streakObj.isActiveToday === true || streakObj.status === 1 || streakObj.streakStatus === 1) {
                    res.litToday = true;
                } else if (streakObj.lastUpdateTime || streakObj.last_update_time) {
                    var ts = Number(streakObj.lastUpdateTime || streakObj.last_update_time);
                    if (ts > 0) {
                        if (ts < 1e12) ts *= 1000;
                        var d = new Date(ts), now = new Date();
                        if (d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth() && d.getDate() === now.getDate()) {
                            res.litToday = true;
                        }
                    }
                }
            }
        }

        var ext = obj.ext || obj.bizExt || (obj.coreInfo && obj.coreInfo.ext);
        if (ext && typeof ext === 'object') {
            var extKeys = Object.keys(ext);
            for (var k = 0; k < extKeys.length; k++) {
                var ek = extKeys[k];
                if (/streak/i.test(ek)) {
                    var ev = ext[ek];
                    if (typeof ev === 'string' && /^\d{1,4}$/.test(ev) && /count|day|num|level/i.test(ek)) {
                        res.days = parseInt(ev, 10);
                    } else if (typeof ev === 'string' && ev.indexOf('{') === 0) {
                        try {
                            var parsed = JSON.parse(ev);
                            var pDays = parsed.days || parsed.count || parsed.streakCount || parsed.streak_count || parsed.level;
                            if (pDays > 0 && pDays < 10000) res.days = parseInt(pDays, 10);
                            if (parsed.isLitToday || parsed.hasSentToday || parsed.status === 1) res.litToday = true;
                        } catch (e) { }
                    }
                }
            }
        }

        return res;
    };

    var getReactFiberKey = function (el) {
        if (!el) return '';
        try {
            var fk = Object.keys(el).find(function (k) {
                return k.startsWith('__reactFiber$') || k.startsWith('__reactInternalInstance$');
            });
            if (!fk) return '';
            var node = el[fk];
            for (var d = 0; d < 6 && node; d++) {
                if (node.key !== null && node.key !== undefined && String(node.key).length > 0) {
                    return String(node.key);
                }
                node = node.return;
            }
        } catch (e) { }
        return '';
    };

    var filterValidRows = function (nodeList) {
        var valid = [];
        for (var i = 0; i < nodeList.length; i++) {
            var el = nodeList[i];
            var h = el.offsetHeight || (el.getBoundingClientRect ? el.getBoundingClientRect().height : 0);
            var txt = (el.innerText || el.textContent || '').trim();
            if (h >= 34 && h <= 240 && (txt.length > 0 || el.querySelector('img'))) {
                valid.push(el);
            }
        }
        return valid;
    };

    var findChatItems = function () {
        var selectors = [
            "[data-e2e='dm-new-conversation-item']",
            "[data-e2e*='dm-new-conversation-item']",
            "[data-e2e*='conversation-item']",
            "[data-e2e='chat-list-item']",
            "[data-e2e*='chat-list-item']",
            "[data-e2e*='chat-item']",
            "[class*='LiInboxItem']",
            "[class*='DivItemWrapper']",
            "[class*='ConversationItem']",
            "[class*='conversation-item']",
            "a[href*='/messages/']"
        ];
        for (var i = 0; i < selectors.length; i++) {
            try {
                var raw = document.querySelectorAll(selectors[i]);
                if (raw.length > 0) {
                    var rows = filterValidRows(raw);
                    if (rows.length > 0) return rows;
                }
            } catch (e) { }
        }
        return [];
    };

    // Pick the ancestor with the LARGEST scrollable range (scrollHeight - clientHeight),
    // falling back to the first overflow-y auto/scroll container even before page 2 overflows.
    var findChatListContainer = function () {
        var rows = chatItems.length > 0 ? chatItems : findChatItems();
        var bestOverflow = null;
        var bestOverflowRange = 0;
        var firstAutoContainer = null;
        var bestFallback = null;
        var bestFallbackRange = 0;

        if (rows.length > 0) {
            var parent = rows[0].parentElement;
            while (parent && parent !== document.body && parent !== document.documentElement) {
                var ch = parent.clientHeight || 0;
                var sh = parent.scrollHeight || 0;
                var range = sh - ch;
                if (ch > 100) {
                    var style = null;
                    try { style = window.getComputedStyle(parent); } catch (e) { }
                    var ov = style ? (style.overflowY || style.overflow || '') : '';
                    if (ov === 'auto' || ov === 'scroll' || ov === 'overlay') {
                        if (!firstAutoContainer) firstAutoContainer = parent;
                        if (range > bestOverflowRange) {
                            bestOverflow = parent;
                            bestOverflowRange = range;
                        }
                    } else if (range > 5 && range > bestFallbackRange) {
                        bestFallback = parent;
                        bestFallbackRange = range;
                    }
                }
                parent = parent.parentElement;
            }
        }
        if (bestOverflow) return bestOverflow;
        if (firstAutoContainer) return firstAutoContainer;
        if (bestFallback) return bestFallback;

        var candidates = document.querySelectorAll(
            '[class*="ChatList"], [class*="chatList"], [class*="ConversationList"], [class*="conversation-list"], [data-e2e*="chat-list"], [data-e2e*="conversation-list"], [class*="ScrollBar"], [class*="DivListWrapper"]'
        );
        for (var i = 0; i < candidates.length; i++) {
            var r = (candidates[i].scrollHeight || 0) - (candidates[i].clientHeight || 0);
            if (r > bestFallbackRange && candidates[i].clientHeight > 100) {
                bestFallback = candidates[i];
                bestFallbackRange = r;
            }
        }
        if (bestFallback) return bestFallback;

        if (document.scrollingElement && document.scrollingElement.scrollHeight > document.scrollingElement.clientHeight + 20) {
            return document.scrollingElement;
        }
        return null;
    };

    var dumpRowDiagnosticsOnce = function (rows) {
        if (diagDumped || !rows || rows.length === 0) return;
        diagDumped = true;
        try {
            var c = findChatListContainer();
            reportLog(
                '[DIAG] rows=' + rows.length +
                ' container=' + (c ? (c.tagName + ' sh=' + c.scrollHeight + ' ch=' + c.clientHeight + ' st=' + c.scrollTop) : 'NULL')
            );
        } catch (e) { }
    };

    var extractRowTitle = function (item) {
        var titleSelectors = [
            "[data-e2e*='nickname']",
            "[data-e2e*='name']",
            "[class*='Nickname']",
            "[class*='nickname']",
            "[class*='InfoNickname']",
            "p",
            "span"
        ];
        for (var i = 0; i < titleSelectors.length; i++) {
            var el = item.querySelector(titleSelectors[i]);
            if (el) {
                var txt = (el.textContent || '').trim();
                if (txt.length > 0 && txt.length <= 60 && !/^\d+$/.test(txt)) return txt;
            }
        }
        var lines = (item.innerText || '').split('\n').map(function (s) { return s.trim(); }).filter(function (s) {
            return s.length > 0 && !/^\d{1,4}$/.test(s);
        });
        return lines.length > 0 ? lines[0].substring(0, 60) : '';
    };

    var buildRowSignature = function (item, rowAvatar) {
        var fKey = getReactFiberKey(item);
        if (fKey && fKey.length >= 3 && !/^\d{1,2}$/.test(fKey)) {
            return 'fk:' + fKey;
        }
        var link = item.querySelector('a[href*="/@"]');
        var href = link ? (link.getAttribute('href') || '') : '';
        if (href) return 'hr:' + href.toLowerCase();
        var title = extractRowTitle(item);
        var fullText = (item.innerText || '').replace(/\s+/g, ' ').trim().substring(0, 80);
        return (title + '|' + fullText + '|' + (rowAvatar || '').slice(-32)).toLowerCase();
    };

    var tryExtractFromFiber = function (item) {
        try {
            var key = Object.keys(item).find(function (k) {
                return k.startsWith('__reactFiber$') || k.startsWith('__reactProps$') || k.startsWith('__reactInternalInstance$');
            });
            if (!key) return null;
            var node = item[key];
            for (var depth = 0; depth < 14 && node; depth++) {
                var props = node.memoizedProps || node;
                if (props && typeof props === 'object') {
                    var candidates = [props.conversation, props.chat, props.item, props.data, props.user, props.otherUser, props];
                    for (var c = 0; c < candidates.length; c++) {
                        var obj = candidates[c];
                        if (!obj || typeof obj !== 'object') continue;
                        var uObj = obj.user || obj.otherUser || obj.peerUser || obj;
                        var uid = uObj.uniqueId || uObj.unique_id || obj.uniqueId;
                        var nick = uObj.nickname || uObj.nickName || obj.nickname || obj.name;
                        var av = uObj.avatarThumb || uObj.avatarMedium || uObj.avatar || obj.avatarUrl || '';
                        if (av && typeof av === 'object' && Array.isArray(av.url_list)) av = av.url_list[0] || '';
                        var isGrp = obj.isGroup === true || obj.type === 2 || obj.conversationType === 2;
                        var st = extractStreakFromObj(obj);
                        if (st.days === 0 && uObj !== obj) st = extractStreakFromObj(uObj);
                        if (isGrp && nick) {
                            return { username: '', displayName: String(nick), isGroup: true, avatarUrl: typeof av === 'string' ? av : '', streakDays: st.days, streakLitToday: st.litToday };
                        }
                        if (uid && typeof uid === 'string' && uid.length >= 2) {
                            return { username: uid, displayName: nick ? String(nick) : extractRowTitle(item), isGroup: false, avatarUrl: typeof av === 'string' ? av : '', streakDays: st.days, streakLitToday: st.litToday };
                        }
                    }
                }
                node = node.return;
            }
        } catch (e) { }
        return null;
    };

    // Harvest any full conversation array already hydrated in parent React fibers
    var harvestConversationsFromAncestorFibers = function (anchorEl) {
        if (!anchorEl) return;
        try {
            var fk = Object.keys(anchorEl).find(function (k) {
                return k.startsWith('__reactFiber$') || k.startsWith('__reactInternalInstance$');
            });
            if (!fk) return;
            var node = anchorEl[fk];
            var inspected = 0;
            while (node && inspected < 28) {
                inspected++;
                var props = node.memoizedProps;
                if (props && typeof props === 'object') {
                    var propKeys = Object.keys(props);
                    for (var p = 0; p < propKeys.length; p++) {
                        var val = props[propKeys[p]];
                        if (Array.isArray(val) && val.length >= 2 && val.length <= 2000 && val[0] && typeof val[0] === 'object') {
                            for (var j = 0; j < val.length; j++) {
                                var entry = val[j];
                                if (!entry || typeof entry !== 'object') continue;
                                var uObj = entry.user || entry.otherUser || entry.peerUser || entry;
                                var uid = uObj.uniqueId || uObj.unique_id || entry.uniqueId;
                                var nick = uObj.nickname || uObj.nickName || entry.nickname || entry.name;
                                var av = uObj.avatarThumb || uObj.avatarMedium || uObj.avatar || entry.avatarUrl || '';
                                if (av && typeof av === 'object' && Array.isArray(av.url_list)) av = av.url_list[0] || '';
                                var isGrp = entry.isGroup === true || entry.type === 2 || entry.conversationType === 2;
                                var st = extractStreakFromObj(entry);
                                if (st.days === 0 && uObj !== entry) st = extractStreakFromObj(uObj);
                                if (isGrp && nick) {
                                    if (reportChat('', String(nick), true, typeof av === 'string' ? av : '', st.days, st.litToday)) stats.store++;
                                } else if (uid && typeof uid === 'string' && uid.length >= 2) {
                                    if (reportChat(uid, nick ? String(nick) : uid, false, typeof av === 'string' ? av : '', st.days, st.litToday)) stats.store++;
                                }
                            }
                        }
                    }
                }
                node = node.return;
            }
        } catch (e) { }
    };

    var getHeaderElement = function () {
        return document.querySelector('[data-e2e="chat-header"]') ||
            document.querySelector('[class*="ChatHeader"]') ||
            document.querySelector('[class*="chatHeader"]') ||
            document.querySelector('[class*="DivChatHeader"]');
    };

    var getHeaderSignature = function () {
        var h = getHeaderElement();
        if (!h) return '';
        var link = h.querySelector('a[href*="/@"]');
        var href = link ? (link.getAttribute('href') || '') : '';
        return href + '|' + (h.textContent || '').trim().substring(0, 60);
    };

    var readOpenChatHeader = function () {
        var header = getHeaderElement();
        if (!header) return null;

        var headerAvatar = extractAvatarFromElement(header);
        var headerStreak = extractStreakFromDom(header);
        var profileLink = header.querySelector('a[href*="/@"]');

        if (profileLink) {
            var href = profileLink.getAttribute('href') || '';
            var match = href.match(/\/@([^\/?#]+)/);
            var username = match ? match[1] : '';
            var nickEl = header.querySelector('[data-e2e*="nickname"], [class*="Nickname"], [class*="nickname"], h1, h2, h3, p');
            var displayName = nickEl ? (nickEl.textContent || '').trim() : '';
            if (!displayName || displayName === '@' + username) {
                var lines = (header.innerText || '').split('\n').map(function (s) { return s.trim(); }).filter(function (s) {
                    return s.length > 0 && !s.startsWith('@') && !/^\d{1,4}$/.test(s);
                });
                displayName = lines.length > 0 ? lines[0] : username;
            }
            if (username) {
                return { username: username, displayName: displayName, isGroup: false, avatarUrl: headerAvatar, streakDays: headerStreak.days, streakLitToday: headerStreak.litToday };
            }
        }

        var allLinks = document.querySelectorAll('a[href*="/@"]');
        for (var j = 0; j < allLinks.length; j++) {
            var pLink = allLinks[j];
            if (pLink.closest('[data-e2e*="conversation-item"]') || pLink.closest('[data-e2e*="chat-list"]') || pLink.closest('[data-e2e*="dm-new-conversation"]')) continue;
            if (pLink.closest('[data-e2e*="nav-"]') || pLink.closest('[data-e2e="profile-icon"]') || pLink.closest('nav')) continue;
            var pHref = pLink.getAttribute('href') || '';
            var pMatch = pHref.match(/\/@([^\/?#]+)/);
            if (pMatch && pMatch[1]) {
                var pLines = (header.innerText || '').split('\n').map(function (s) { return s.trim(); }).filter(function (s) {
                    return s.length > 0 && !s.startsWith('@') && !/^\d{1,4}$/.test(s);
                });
                return { username: pMatch[1], displayName: pLines[0] || pMatch[1], isGroup: false, avatarUrl: headerAvatar, streakDays: headerStreak.days, streakLitToday: headerStreak.litToday };
            }
        }

        var rawLines = (header.innerText || '').split('\n').map(function (s) { return s.trim(); }).filter(function (s) {
            return s.length > 0 && !/^\d{1,4}$/.test(s);
        });
        var groupTitle = rawLines.length > 0 ? rawLines[0] : (header.textContent || '').trim();
        var hasGroupClass = !!header.querySelector('[class*="group"], [class*="Group"], [class*="member"], [class*="Member"]');
        var memberMatch = (header.textContent || '').match(/(\d+)\s*(member|thành viên)/i);
        var avatars = header.querySelectorAll('img');
        if (groupTitle && (hasGroupClass || memberMatch || avatars.length > 1 || !profileLink)) {
            return { username: '', displayName: groupTitle.substring(0, 60), isGroup: true, avatarUrl: headerAvatar, streakDays: headerStreak.days, streakLitToday: headerStreak.litToday };
        }
        return null;
    };

    var waitForHeaderChange = function (prevSig, startedAt, onReady) {
        if (finished) return;
        var curSig = getHeaderSignature();
        if (curSig && curSig !== prevSig) {
            onReady(true);
            return;
        }
        if (Date.now() - startedAt >= 1500) {
            onReady(false);
            return;
        }
        setTimeout(function () { waitForHeaderChange(prevSig, startedAt, onReady); }, 45);
    };

    var scanNextItem = function () {
        if (finished) return;
        if (discoveredCount >= maxChatsToScan) {
            reportDone('');
            return;
        }
        if (chatIndex >= chatItems.length) {
            scrollMore();
            return;
        }

        var item = chatItems[chatIndex];
        chatIndex++;

        if (!item || !item.isConnected) {
            setTimeout(scanNextItem, 0);
            return;
        }

        var rowAvatar = extractAvatarFromElement(item);
        var rowSig = buildRowSignature(item, rowAvatar);
        if (rowSig && seenRowSigs[rowSig]) {
            setTimeout(scanNextItem, 0);
            return;
        }

        var rowStreak = extractStreakFromDom(item);

        // 1) Instant inline profile link path (0ms)
        var inlineLinks = item.querySelectorAll('a[href*="/@"]');
        for (var i = 0; i < inlineLinks.length; i++) {
            var href = inlineLinks[i].getAttribute('href') || '';
            var m = href.match(/\/@([^\/?#]+)/);
            if (m && m[1]) {
                if (rowSig) seenRowSigs[rowSig] = true;
                var title = extractRowTitle(item) || m[1];
                if (reportChat(m[1], title, false, rowAvatar, rowStreak.days, rowStreak.litToday)) {
                    stats.link++;
                    reportProgress('Đã tìm thấy ' + discoveredCount + ' cuộc trò chuyện');
                }
                setTimeout(scanNextItem, 0);
                return;
            }
        }

        // 2) Instant React fiber path (0ms)
        var fiberInfo = tryExtractFromFiber(item);
        if (fiberInfo) {
            if (rowSig) seenRowSigs[rowSig] = true;
            var av = fiberInfo.avatarUrl || rowAvatar;
            var days = fiberInfo.streakDays || rowStreak.days;
            var lit = fiberInfo.streakLitToday || rowStreak.litToday;
            if (reportChat(fiberInfo.username, fiberInfo.displayName, fiberInfo.isGroup, av, days, lit)) {
                stats.fiber++;
                reportProgress('Đã tìm thấy ' + discoveredCount + ' cuộc trò chuyện');
            }
            setTimeout(scanNextItem, 0);
            return;
        }

        // 3) Click-and-read-header fallback (only mark seenRowSigs AFTER header actually updates!)
        var container = findChatListContainer();
        if (container) savedScrollTop = Math.max(savedScrollTop, container.scrollTop);
        var prevSig = lastHeaderSignature || getHeaderSignature();
        try {
            item.click();
        } catch (e) {
            setTimeout(scanNextItem, 10);
            return;
        }

        waitForHeaderChange(prevSig, Date.now(), function (changed) {
            if (finished) return;
            var curSig = getHeaderSignature();
            if (changed || !prevSig) {
                lastHeaderSignature = curSig;
                var info = readOpenChatHeader();
                if (info) {
                    if (rowSig) seenRowSigs[rowSig] = true;
                    var finalAvatar = rowAvatar || info.avatarUrl;
                    var finalDays = rowStreak.days || info.streakDays;
                    var finalLit = rowStreak.litToday || info.streakLitToday;
                    if (reportChat(info.username, info.displayName, info.isGroup, finalAvatar, finalDays, finalLit)) {
                        stats.click++;
                        reportProgress('Đã tìm thấy ' + discoveredCount + ' cuộc trò chuyện');
                    }
                }
            } else {
                stats.clickMiss++;
                var tries = (failedRowAttempts[rowSig] || 0) + 1;
                failedRowAttempts[rowSig] = tries;
                if (tries >= 2 && rowSig) {
                    // Already selected row (same header as currently open chat) — read it once and mark seen
                    var sameInfo = readOpenChatHeader();
                    if (sameInfo) {
                        var av2 = rowAvatar || sameInfo.avatarUrl;
                        var d2 = rowStreak.days || sameInfo.streakDays;
                        var l2 = rowStreak.litToday || sameInfo.streakLitToday;
                        if (reportChat(sameInfo.username, sameInfo.displayName, sameInfo.isGroup, av2, d2, l2)) {
                            stats.click++;
                            reportProgress('Đã tìm thấy ' + discoveredCount + ' cuộc trò chuyện');
                        }
                    }
                    seenRowSigs[rowSig] = true;
                }
            }
            setTimeout(scanNextItem, 20);
        });
    };

    var scrollMore = function () {
        if (finished) return;
        scrollAttempts++;
        if (scrollAttempts > maxScrollAttempts) {
            reportDone('');
            return;
        }
        var container = findChatListContainer();
        if (!container) {
            reportLog('[SCROLL] No scrollable container found');
            reportDone('');
            return;
        }

        // Restore scrollTop if clicking an item bounced the sidebar back up
        if (savedScrollTop > container.scrollTop + 20) {
            container.scrollTop = savedScrollTop;
        }

        var prevScrollTop = container.scrollTop;
        var prevScrollHeight = container.scrollHeight;
        // 60% viewport step -> 40% overlap guarantees virtualized rows are never skipped
        var step = Math.max(200, Math.floor(container.clientHeight * 0.6));
        container.scrollTop = prevScrollTop + step;
        savedScrollTop = container.scrollTop;

        try {
            container.dispatchEvent(new Event('scroll', { bubbles: true }));
            container.dispatchEvent(new WheelEvent('wheel', { deltaY: step, bubbles: true }));
        } catch (e) { }

        // If already near bottom, also nudge last DOM row into view to fire IntersectionObserver sentinels
        var atBottom = container.scrollTop <= prevScrollTop + 2 ||
            (container.scrollTop + container.clientHeight >= container.scrollHeight - 24);
        if (atBottom && chatItems.length > 0) {
            try {
                var lastRow = chatItems[chatItems.length - 1];
                if (lastRow && lastRow.scrollIntoView) {
                    lastRow.scrollIntoView({ block: 'end' });
                }
            } catch (e) { }
        }

        reportProgress('Đang tải thêm đoạn chat... (' + discoveredCount + ')');

        var waitMs = atBottom ? 1300 : 420;
        var startedAt = Date.now();
        var pollScroll = function () {
            if (finished) return;
            var moved = container.scrollTop > prevScrollTop + 2;
            var grew = container.scrollHeight > prevScrollHeight + 20;
            var elapsed = Date.now() - startedAt;

            if (grew && !moved) {
                container.scrollTop = prevScrollTop + step;
                savedScrollTop = container.scrollTop;
                moved = container.scrollTop > prevScrollTop + 2;
            }

            if ((moved && elapsed >= 280) || elapsed >= waitMs) {
                chatItems = findChatItems();
                if (chatItems.length > 0) {
                    harvestConversationsFromAncestorFibers(chatItems[0]);
                }
                if (!moved && !grew && newFoundInPass === 0) {
                    staleBottomRetries++;
                    reportLog(
                        '[SCROLL] Bottom check ' + staleBottomRetries + '/' + maxStaleBottomRetries +
                        ' st=' + Math.round(container.scrollTop) +
                        ' ch=' + container.clientHeight +
                        ' sh=' + container.scrollHeight +
                        ' domRows=' + chatItems.length +
                        ' total=' + discoveredCount
                    );
                    if (staleBottomRetries >= maxStaleBottomRetries) {
                        reportDone('');
                        return;
                    }
                    // Bounce slightly up then back to bottom to re-trigger IntersectionObserver lazy pagination
                    if (staleBottomRetries % 2 === 1 && container.scrollHeight > container.clientHeight + 100) {
                        container.scrollTop = Math.max(0, container.scrollHeight - container.clientHeight - 160);
                        try { container.dispatchEvent(new Event('scroll', { bubbles: true })); } catch (e) { }
                        setTimeout(function () {
                            if (finished) return;
                            container.scrollTop = container.scrollHeight;
                            try {
                                container.dispatchEvent(new Event('scroll', { bubbles: true }));
                                container.dispatchEvent(new WheelEvent('wheel', { deltaY: 600, bubbles: true }));
                            } catch (e) { }
                            setTimeout(scrollMore, 1100);
                        }, 180);
                    } else {
                        container.scrollTop = container.scrollHeight;
                        try {
                            container.dispatchEvent(new Event('scroll', { bubbles: true }));
                            container.dispatchEvent(new WheelEvent('wheel', { deltaY: 600, bubbles: true }));
                        } catch (e) { }
                        setTimeout(scrollMore, 1100);
                    }
                    return;
                }
                staleBottomRetries = 0;
                newFoundInPass = 0;
                chatIndex = 0;
                scanNextItem();
                return;
            }
            setTimeout(pollScroll, 80);
        };
        setTimeout(pollScroll, 130);
    };

    var waitForInboxReady = function (startedAt) {
        if (finished) return;
        var rows = findChatItems();
        if (rows.length > 0) {
            setTimeout(function () {
                if (finished) return;
                chatItems = findChatItems();
                if (chatItems.length === 0) chatItems = rows;
                reportLog('[FAST-SCAN] Inbox ready in ' + (Date.now() - startedAt) + 'ms (' + chatItems.length + ' real rows)');
                dumpRowDiagnosticsOnce(chatItems);
                harvestConversationsFromAncestorFibers(chatItems[0]);
                newFoundInPass = 0;
                chatIndex = 0;
                scanNextItem();
            }, 450);
            return;
        }
        if (Date.now() - startedAt >= 15000) {
            reportDone('Không tìm thấy cuộc trò chuyện nào. Hãy đảm bảo đã đăng nhập TikTok.');
            return;
        }
        setTimeout(function () { waitForInboxReady(startedAt); }, 150);
    };

    extractOwnProfile();
    reportProgress('Đang quét hộp thư TikTok...');
    waitForInboxReady(Date.now());
})();
