// 只适配本模块，使用主工程已加载的Bootstrap，不改公共弹窗组件。
(function() {
    'use strict';
    const style = document.createElement('style');
    style.textContent = '.gh-modal .modal-dialog{width:850px;max-width:calc(100vw - 24px);margin:24px auto}.gh-modal .modal-content,.gh-modal .modal-content>form{display:flex;flex-direction:column;max-height:calc(100vh - 48px)}.gh-modal .modal-header,.gh-modal .modal-footer{flex:none}.gh-modal .modal-body{overflow:auto;min-height:0}.gh-modal .modal-footer{text-align:right;display:block}.gh-modal label{max-width:100%}.gh-modal input,.gh-modal select{max-width:100%;min-height:34px;font-size:14px}';
    document.head.append(style);
    document.querySelectorAll('.greenhouse-module dialog,.resources dialog').forEach(old => {
        const modal = document.createElement('div');
        modal.id = old.id;
        modal.className = 'modal fade gh-modal';
        modal.tabIndex = -1;
        modal.setAttribute('role', 'dialog');
        modal.setAttribute('aria-modal', 'true');
        const dialog = document.createElement('div');
        dialog.className = 'modal-dialog';
        const content = document.createElement('div');
        content.className = 'modal-content';
        dialog.append(content);
        modal.append(dialog);
        const title = old.querySelector('h2'),
            header = document.createElement('div');
        header.className = 'modal-header';
        const close = document.createElement('button');
        close.type = 'button';
        close.className = 'close';
        close.setAttribute('data-dismiss', 'modal');
        close.setAttribute('aria-label', '关闭');
        close.textContent = '×';
        header.append(close);
        const heading = document.createElement('h4');
        heading.className = 'modal-title';
        heading.textContent = title ? title.textContent : '详情';
        heading.id = title && title.id ? title.id : modal.id + 'Title';
        modal.setAttribute('aria-labelledby', heading.id);
        header.append(heading);
        if (title) title.remove();
        const form = old.querySelector('form'),
            body = document.createElement('div'),
            footer = document.createElement('div');
        body.className = 'modal-body';
        footer.className = 'modal-footer';
        const container = form || old;
        const action = container.querySelector('.resource-actions');
        if (action) {
            Array.from(action.childNodes).forEach(n => footer.append(n));
            action.remove();
        } else {
            const buttons = Array.from(container.querySelectorAll('button'));
            buttons.forEach(b => footer.append(b));
        }
        if (form) {
            const hints = Array.from(old.childNodes).filter(n => n !== form);
            hints.forEach(n => body.append(n));
            Array.from(form.childNodes).forEach(n => body.append(n));
            form.append(header, body, footer);
            content.append(form);
            const save = footer.querySelector('button:not([type]),button[type="submit"]');
            if (save) {
                save.type = 'submit';
                save.classList.remove('default');
                save.classList.add('blue');
            }
        } else {
            Array.from(old.childNodes).forEach(n => body.append(n));
            content.append(header, body, footer);
        }
        modal.querySelectorAll('[required]').forEach(input => {
            const label = input.closest('label');
            if (!label) return;
            const marker = document.createElement('span');
            marker.textContent = ' *';
            marker.title = '必填';
            marker.style.color = '#a42d28';
            label.insertBefore(marker, input);
        });
        old.replaceWith(modal);
        modal.showModal = () => window.jQuery(modal).modal({
            backdrop: 'static',
            show: true
        });
        modal.close = () => window.jQuery(modal).modal('hide');
    });
})();
