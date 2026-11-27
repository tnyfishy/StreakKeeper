// High-Speed TikTok Streak Automation Script
// Features:
// - Zero blind sleeps: 120ms DOM readiness polling + 45ms adaptive header polling + fast Draft.js paste
// - Fast Fiber & inline-link pre-matching (zero clicks on non-target rows when available)
// - Today's streak detection: skips sending if user already has an active lit streak today
// - Live metadata sync: reports avatarUrl, streakDays, streakLitToday back to app

(function () {
    var userName = '[UserName]';
    var message = '[Message]';
    var isGroup = '[IsGroup]' === 'true';
    var skipIfLitToday = '[SkipIfLitToday]' === 'true';

    var found = false;
    var done = false;
    var chatIndex = 0;
    var chatItems = [];
    var maxScrollAttempts = 40;
    var scrollAttempts = 0;
    var checkedKeys = {};
    var matchedRowStreak = { days: 0, litToday: false };
    var matchedRowAvatar = '';
    var lastHeaderSig = '';

    var log = function (msg) {
        if (typeof StreakApp === 'undefined') {
            console.log(msg);
            return;
        }
        StreakApp.log(msg);
    };

    var reportMeta = function (avatarUrl, streakDays, litToday) {
        if (typeof StreakApp !== 'undefined' && typeof StreakApp.onFriendMeta === 'function') {
            try {
                StreakApp.onFriendMeta(userName, avatarUrl || '', streakDays > 0 ? Math.floor(streakDays) : 0, !!litToday);
            } catch (e) { }
        }
    };

    var reportSuccess = function (note) {
        if (done) return;
        done = true;
        log('[RESULT] OK — ' + userName + (note ? ' (' + note + ')' : ''));
        if (typeof StreakApp !== 'undefined') {
            StreakApp.onMessageSent(userName, true, note || '');
        }
    };

    var reportError = function (err) {
        if (done) return;
        done = true;
        log('[RESULT] FAIL — ' + err);
        if (typeof StreakApp !== 'undefined') {
            StreakApp.onMessageSent(userName, false, err);
        }
    };

    // ── Avatar & Streak Metadata Extraction ──────────────────────────────────

    var extractAvatar = function (el) {
        if (!el) return '';
        var imgs = el.querySelectorAll('img');
        for (var i = 0; i < imgs.length; i++) {
            var src = imgs[i].currentSrc || imgs[i].getAttribute('src') || '';
            if (src && src.indexOf('http') === 0 && src.indexOf('streak') === -1 && src.indexOf('emoji') === -1) {
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
        return cls.indexOf('unread') !== -1 || cls.indexOf('badge') !== -1 || cls.indexOf('time') !== -1 || cls.indexOf('dot') !== -1 ||
            e2e.indexOf('unread') !== -1 || e2e.indexOf('time') !== -1;
    };

    var extractStreakDom = function (root) {
        var res = { days: 0, litToday: false };
        if (!root) return res;

        var els = root.querySelectorAll('[data-e2e*="streak" i], [class*="streak" i], [class*="Streak"], [class*="flame" i], [class*="Flame"], [aria-label*="streak" i], [aria-label*="chuỗi" i]');
        for (var i = 0; i < els.length; i++) {
            var el = els[i];
            if (isUnreadOrTimeEl(el)) continue;
            var txt = (el.textContent || '').trim();
            if (/\d+:\d+/.test(txt)) continue;
            var m = txt.match(/^\D*(\d{1,4})\D*$/);
            if (m) {
                var days = parseInt(m[1], 10);
                if (days > 0 && days < 10000) {
                    res.days = days;
                    var cls = (el.className || '').toString().toLowerCase();
                    var html = (el.innerHTML || '').toLowerCase();
                    var col = '';
                    try { col = window.getComputedStyle(el).color || ''; } catch (e) { }
                    var inactive = cls.indexOf('gray') !== -1 || cls.indexOf('grey') !== -1 ||
                        cls.indexOf('inactive') !== -1 || cls.indexOf('expire') !== -1 ||
                        cls.indexOf('dim') !== -1 || cls.indexOf('pending') !== -1 ||
                        txt.indexOf('\u23F3') !== -1 || html.indexOf('hourglass') !== -1;
                    res.litToday = !inactive && (
                        cls.indexOf('active') !== -1 || cls.indexOf('lit') !== -1 ||
                        isWarmFlameColor(col) || isWarmFlameColor(html)
                    );
                    return res;
                }
            }
        }

        // Never regex raw innerText for 🔥 + digits: message previews like "🔥🔥🔥\n10:44"
        // falsely match the timestamp hour ("10") as streak days!
        return res;
    };

    var extractStreakObj = function (obj) {
        var res = { days: 0, litToday: false };
        if (!obj || typeof obj !== 'object') return res;

        var directDays = obj.streakCount || obj.streak_count || obj.streakDays || obj.streak_days || obj.consecutiveDays;
        if (typeof directDays === 'number' && directDays > 0 && directDays < 10000) res.days = directDays;
        else if (typeof directDays === 'string' && /^\d{1,4}$/.test(directDays)) res.days = parseInt(directDays, 10);

        var st = obj.streak || obj.streakInfo || obj.streak_info || obj.petStreak || obj.streakData;
        if (st && typeof st === 'object') {
            var raw = st.streakCount || st.streak_count || st.streakDays || st.streak_days || st.days || st.count || st.level;
            if (typeof raw === 'number' && raw > 0 && raw < 10000) res.days = raw;
            else if (typeof raw === 'string' && /^\d{1,4}$/.test(raw)) res.days = parseInt(raw, 10);

            if (res.days > 0) {
                if (st.isLitToday === true || st.hasSentToday === true || st.isActiveToday === true || st.status === 1 || st.streakStatus === 1) {
                    res.litToday = true;
                } else if (st.lastUpdateTime || st.last_update_time) {
                    var ts = Number(st.lastUpdateTime || st.last_update_time);
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
        return res;
    };

    var inspectFiberRow = function (item) {
        try {
            var key = Object.keys(item).find(function (k) {
                return k.startsWith('__reactFiber$') || k.startsWith('__reactProps$') || k.startsWith('__reactInternalInstance$');
            });
            if (!key) return null;
            var node = item[key];
            for (var depth = 0; depth < 14 && node; depth++) {
                var props = node.memoizedProps || node;
                if (props && typeof props === 'object') {
                    var list = [props.conversation, props.chat, props.item, props.data, props.user, props.otherUser, props];
                    for (var c = 0; c < list.length; c++) {
                        var obj = list[c];
                        if (!obj || typeof obj !== 'object') continue;
                        var uObj = obj.user || obj.otherUser || obj.peerUser || obj;
                        var uid = uObj.uniqueId || uObj.unique_id || obj.uniqueId || '';
                        var nick = uObj.nickname || uObj.nickName || obj.nickname || obj.name || '';
                        var isGrp = obj.isGroup === true || obj.type === 2 || obj.conversationType === 2;
                        var st = extractStreakObj(obj);
                        if (st.days === 0) st = extractStreakObj(uObj);
                        if ((isGrp && nick) || (!isGrp && uid)) {
                            return { username: String(uid), displayName: String(nick), isGroup: isGrp, streak: st };
                        }
                    }
                }
                node = node.return;
            }
        } catch (e) { }
        return null;
    };

    // Checks open chat panel to see if today's streak is already lit / extended today
    var detectAlreadyLitTodayInChat = function () {
        if (matchedRowStreak.litToday && matchedRowStreak.days > 0) {
            return true;
        }
        var header = getHeader();
        var hStreak = extractStreakDom(header);
        if (hStreak.days > 0 && hStreak.days > matchedRowStreak.days) matchedRowStreak.days = hStreak.days;
        if (hStreak.litToday && hStreak.days > 0) {
            matchedRowStreak.litToday = true;
            return true;
        }

        // Check chat message list for today's streak notice or outgoing message sent today
        var chatMain = document.querySelector('[data-e2e*="chat-main"], [class*="ChatMain"], [class*="MessageList"], [class*="DivChatMain"]');
        if (!chatMain) return false;

        var timeLabels = chatMain.querySelectorAll('[class*="Time"], [class*="time"], [data-e2e*="time"]');
        var hasTodayDivider = false;
        var todayDividerEl = null;
        for (var i = timeLabels.length - 1; i >= 0; i--) {
            var t = (timeLabels[i].textContent || '').trim();
            // Today timestamps on TikTok Web are either "Today HH:mm", "Hôm nay HH:mm", or bare "HH:mm" / "HH:mm AM/PM"
            if (/^(today|hôm nay|\d{1,2}:\d{2}(\s*(am|pm))?)$/i.test(t) || /^(today|hôm nay)\s+\d{1,2}:\d{2}/i.test(t)) {
                hasTodayDivider = true;
                todayDividerEl = timeLabels[i];
                break;
            }
            // If we hit a clearly older date divider ("Yesterday", "May 12", "2026-..."), stop searching
            if (/(yesterday|hôm qua|202\d|jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec|thg)/i.test(t)) {
                break;
            }
        }

        if (!hasTodayDivider) return false;

        // Check if a streak-extended system notice appeared in today's section
        var fullText = (chatMain.innerText || '');
        var idxToday = todayDividerEl ? fullText.lastIndexOf((todayDividerEl.textContent || '').trim()) : -1;
        var todaySlice = idxToday >= 0 ? fullText.substring(idxToday) : fullText.slice(-500);
        if (/(streak|chuỗi).*(day|ngày|keep|giữ|alive|extended|tăng|lit)/i.test(todaySlice) ||
            /(\d+)\s*(day|ngày)\s*(streak|chuỗi)/i.test(todaySlice)) {
            matchedRowStreak.litToday = true;
            return true;
        }

        // Check if we already sent an outgoing message in today's section (message bubble on right side)
        var chatRect = chatMain.getBoundingClientRect();
        if (chatRect && chatRect.width > 100) {
            var bubbles = chatMain.querySelectorAll('[data-e2e*="chat-item"], [class*="MessageItem"], [class*="ChatItem"], [class*="TextContainer"]');
            var checkStart = Math.max(0, bubbles.length - 6);
            for (var b = bubbles.length - 1; b >= checkStart; b--) {
                var el = bubbles[b];
                if (todayDividerEl && (todayDividerEl.compareDocumentPosition(el) & Node.DOCUMENT_POSITION_FOLLOWING) === 0) {
                    continue;
                }
                var r = el.getBoundingClientRect();
                var centerX = r.left + r.width / 2;
                var cls = (el.className || '').toString().toLowerCase();
                // Outgoing messages sit on the right half or carry "self"/"sender"/"right" class
                if (r.width > 10 && (centerX > chatRect.left + chatRect.width * 0.58 || cls.indexOf('self') !== -1 || cls.indexOf('sender') !== -1)) {
                    // Only treat as lit today if this chat already has a streak OR we explicitly sent a message today
                    matchedRowStreak.litToday = true;
                    return true;
                }
            }
        }
        return false;
    };

    // ── Chat Discovery & Header Helpers ──────────────────────────────────────

    var filterValidRows = function (nodeList) {
        var valid = [];
        for (var i = 0; i < nodeList.length; i++) {
            var el = nodeList[i];
            var h = el.offsetHeight || (el.getBoundingClientRect ? el.getBoundingClientRect().height : 0);
            var txt = (el.innerText || el.textContent || '').trim();
            if (h >= 34 && h <= 220 && (txt.length > 0 || el.querySelector('img'))) {
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

    var findChatListContainer = function () {
        if (chatItems.length > 0) {
            var parent = chatItems[0].parentElement;
            while (parent && parent !== document.body) {
                if (parent.scrollHeight > parent.clientHeight + 10) return parent;
                parent = parent.parentElement;
            }
        }
        var candidates = document.querySelectorAll('[class*="ChatList"], [class*="chatList"], [class*="conversation-list"]');
        for (var i = 0; i < candidates.length; i++) {
            if (candidates[i].scrollHeight > candidates[i].clientHeight + 10) return candidates[i];
        }
        return null;
    };

    var getHeader = function () {
        return document.querySelector('[data-e2e="chat-header"]') ||
            document.querySelector('[class*="ChatHeader"]') ||
            document.querySelector('[class*="chatHeader"]') ||
            document.querySelector('[class*="DivChatHeader"]');
    };

    var getHeaderSig = function () {
        var h = getHeader();
        if (!h) return '';
        var a = h.querySelector('a[href*="/@"]');
        return (a ? (a.getAttribute('href') || '') : '') + '|' + (h.textContent || '').trim().substring(0, 60);
    };

    var findCurrentChatUsername = function () {
        var header = getHeader();
        if (header) {
            var link = header.querySelector('a[href*="/@"]');
            if (link) {
                var m = (link.getAttribute('href') || '').match(/\/@([^\/?#]+)/);
                return m ? m[1] : '';
            }
        }
        var allLinks = document.querySelectorAll('a[href*="/@"]');
        for (var j = 0; j < allLinks.length; j++) {
            var pLink = allLinks[j];
            if (pLink.closest('[data-e2e*="conversation-item"]') || pLink.closest('[data-e2e*="chat-list"]') || pLink.closest('[data-e2e*="dm-new-conversation"]')) continue;
            if (pLink.closest('[data-e2e*="nav-"]') || pLink.closest('[data-e2e="profile-icon"]') || pLink.closest('nav')) continue;
            var pm = (pLink.getAttribute('href') || '').match(/\/@([^\/?#]+)/);
            if (pm && pm[1]) return pm[1];
        }
        return '';
    };

    var findCurrentChatGroupName = function () {
        var header = getHeader();
        if (!header) return '';
        if (header.querySelector('a[href*="/@"]')) return '';
        return (header.textContent || '').trim();
    };

    var isTargetUser = function (currentName) {
        if (!currentName || !userName) return false;
        var a = currentName.toLowerCase().trim();
        var b = userName.toLowerCase().trim();
        return isGroup ? (a.indexOf(b) !== -1) : (a === b);
    };

    // ── Fast Message Typing & Sending ────────────────────────────────────────

    var findMessageInput = function () {
        return document.querySelector('[class*="DraftEditor-editorContainer"] [contenteditable="true"]') ||
            document.querySelector('[class*="DraftEditor-root"] [contenteditable="true"]') ||
            document.querySelector('div[contenteditable="true"][role="textbox"]') ||
            document.querySelector('div[contenteditable="true"]');
    };

    var findDraftEditor = function (messageInput) {
        var key = Object.keys(messageInput).find(function (k) {
            return k.startsWith('__reactFiber$') || k.startsWith('__reactInternalInstance$');
        });
        if (!key) return null;
        var cur = messageInput[key];
        while (cur) {
            if (cur.stateNode && cur.stateNode.editor) return cur.stateNode;
            cur = cur.return;
        }
        return null;
    };

    var typeAndSend = function (messageInput) {
        var draftEditor = findDraftEditor(messageInput);

        var dispatchSend = function () {
            var sendBtn = document.querySelector('[data-e2e*="send"]') ||
                document.querySelector('[data-e2e*="Send"]') ||
                document.querySelector('button[type="submit"]');
            if (sendBtn) {
                sendBtn.dispatchEvent(new Event('click', { bubbles: true }));
            } else {
                messageInput.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true, cancelable: true }));
                messageInput.dispatchEvent(new KeyboardEvent('keyup', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }));
            }

            // Confirm input cleared quickly (poll every 50ms up to 450ms instead of blind 1000ms)
            var started = Date.now();
            var confirmClear = function () {
                var rem = (messageInput.textContent || '').trim();
                if (rem.length === 0 || Date.now() - started >= 450) {
                    var hAvatar = matchedRowAvatar || extractAvatar(getHeader());
                    reportMeta(hAvatar, matchedRowStreak.days, true);
                    reportSuccess('');
                    return;
                }
                setTimeout(confirmClear, 50);
            };
            setTimeout(confirmClear, 90);
        };

        if (draftEditor) {
            draftEditor.focus();
            setTimeout(function () {
                var dt = new DataTransfer();
                dt.setData('text/plain', message);
                var ev = new ClipboardEvent('paste', { bubbles: true, cancelable: true, clipboardData: dt });
                try { draftEditor._onPaste(ev); } catch (e) { }
                setTimeout(dispatchSend, 110);
            }, 60);
        } else {
            messageInput.click();
            messageInput.focus();
            setTimeout(function () {
                var sel = window.getSelection();
                var range = document.createRange();
                range.selectNodeContents(messageInput);
                range.collapse(false);
                sel.removeAllRanges();
                sel.addRange(range);
                document.execCommand('insertText', false, message);
                setTimeout(dispatchSend, 110);
            }, 60);
        }
    };

    var handleMatchedOpenChat = function () {
        var headerAvatar = matchedRowAvatar || extractAvatar(getHeader());
        var headerStreak = extractStreakDom(getHeader());
        if (headerStreak.days > matchedRowStreak.days) matchedRowStreak.days = headerStreak.days;
        if (headerStreak.litToday) matchedRowStreak.litToday = true;

        if (skipIfLitToday && detectAlreadyLitTodayInChat()) {
            log('[SKIP-STREAK] Đã có chuỗi hôm nay cho ' + userName + ' (' + matchedRowStreak.days + ' ngày)');
            reportMeta(headerAvatar, matchedRowStreak.days, true);
            reportSuccess('ALREADY_STREAK_TODAY');
            return;
        }

        // Wait briefly for message input to mount (poll every 45ms up to 1200ms)
        var started = Date.now();
        var waitInput = function () {
            if (done) return;
            var input = findMessageInput();
            if (input) {
                typeAndSend(input);
                return;
            }
            if (Date.now() - started >= 1200) {
                reportError('Message input not found');
                return;
            }
            setTimeout(waitInput, 45);
        };
        waitInput();
    };

    // Adaptive wait for header switch (45ms poll, 80-180ms typical instead of 1500ms!)
    var waitForHeaderChange = function (prevSig, startedAt, onReady) {
        if (done) return;
        var sig = getHeaderSig();
        if (sig && sig !== prevSig) {
            onReady();
            return;
        }
        if (Date.now() - startedAt >= 700) {
            onReady();
            return;
        }
        setTimeout(function () { waitForHeaderChange(prevSig, startedAt, onReady); }, 45);
    };

    var scrollAndRetry = function () {
        scrollAttempts++;
        if (scrollAttempts > maxScrollAttempts) {
            reportError('User not found in chat list');
            return;
        }
        var container = findChatListContainer();
        if (!container) {
            reportError('User not found in chat list');
            return;
        }
        var prevScrollTop = container.scrollTop;
        var prevCount = chatItems.length;
        container.scrollTop += container.clientHeight * 3;

        var pollScroll = function (startedAt) {
            if (done) return;
            var fresh = findChatItems();
            var moved = container.scrollTop > prevScrollTop + 2;
            if ((moved && (fresh.length !== prevCount || Date.now() - startedAt >= 320)) || Date.now() - startedAt >= 850) {
                chatItems = fresh;
                if (!moved) {
                    reportError('User not found in chat list');
                    return;
                }
                chatIndex = Math.max(0, prevCount - 12);
                checkNextChat();
                return;
            }
            setTimeout(function () { pollScroll(startedAt); }, 60);
        };
        setTimeout(function () { pollScroll(Date.now()); }, 120);
    };

    var checkNextChat = function () {
        if (found || done) return;
        if (chatIndex >= chatItems.length) {
            scrollAndRetry();
            return;
        }

        var item = chatItems[chatIndex];
        chatIndex++;

        var itemLinks = item.querySelectorAll('a[href*="/@"]');
        if (isGroup && itemLinks.length > 0) {
            setTimeout(checkNextChat, 0);
            return;
        }

        // Fast path 1: inline profile link match (0ms)
        if (!isGroup && itemLinks.length > 0) {
            for (var i = 0; i < itemLinks.length; i++) {
                var m = (itemLinks[i].getAttribute('href') || '').match(/\/@([^\/?#]+)/);
                if (m && m[1]) {
                    var k = m[1].toLowerCase().trim();
                    if (isTargetUser(m[1])) {
                        found = true;
                        matchedRowAvatar = extractAvatar(item);
                        matchedRowStreak = extractStreakDom(item);
                        var prevS = getHeaderSig();
                        item.click();
                        waitForHeaderChange(prevS, Date.now(), handleMatchedOpenChat);
                        return;
                    }
                    checkedKeys[k] = true;
                    setTimeout(checkNextChat, 0);
                    return;
                }
            }
        }

        // Fast path 2: React Fiber match (0ms — skips non-matching rows without clicking!)
        var fiber = inspectFiberRow(item);
        if (fiber) {
            if (fiber.isGroup === isGroup) {
                var candidate = isGroup ? fiber.displayName : fiber.username;
                if (isTargetUser(candidate)) {
                    found = true;
                    matchedRowAvatar = extractAvatar(item);
                    matchedRowStreak = fiber.streak.days > 0 ? fiber.streak : extractStreakDom(item);
                    var prevF = getHeaderSig();
                    item.click();
                    waitForHeaderChange(prevF, Date.now(), handleMatchedOpenChat);
                    return;
                }
                if (candidate) checkedKeys[candidate.toLowerCase().trim()] = true;
            }
            setTimeout(checkNextChat, 0);
            return;
        }

        // Fallback: click row and wait adaptively for header update (~100ms instead of 1500ms)
        var prevSig = lastHeaderSig || getHeaderSig();
        item.click();
        waitForHeaderChange(prevSig, Date.now(), function () {
            if (done) return;
            lastHeaderSig = getHeaderSig();
            var curName = isGroup ? findCurrentChatGroupName() : findCurrentChatUsername();
            var key = curName ? curName.toLowerCase().trim() : '';
            if (key) checkedKeys[key] = true;
            if (isTargetUser(curName)) {
                found = true;
                matchedRowAvatar = extractAvatar(item);
                matchedRowStreak = extractStreakDom(item);
                handleMatchedOpenChat();
                return;
            }
            setTimeout(checkNextChat, 15);
        });
    };

    // ── Instant Start ────────────────────────────────────────────────────────

    var init = function () {
        try {
            if (userName.startsWith('@')) userName = userName.substring(1);

            // Already open on target chat? Act immediately (0ms)!
            var preOpen = isGroup ? findCurrentChatGroupName() : findCurrentChatUsername();
            if (preOpen && isTargetUser(preOpen)) {
                found = true;
                handleMatchedOpenChat();
                return;
            }

            // Adaptive poll until chatItems render (0ms if already loaded, max 10s on cold load)
            var startedAt = Date.now();
            var waitReady = function () {
                if (done) return;
                chatItems = findChatItems();
                if (chatItems.length > 0) {
                    checkNextChat();
                    return;
                }
                if (Date.now() - startedAt >= 10000) {
                    reportError('No chat items found after 10s wait');
                    return;
                }
                setTimeout(waitReady, 120);
            };
            waitReady();
        } catch (e) {
            reportError('Error: ' + e.message);
        }
    };

    init();
})();
