<%@ page pageEncoding="UTF-8" %>

<div class="modal fade" id="todo_modal" tabindex="-1"
     role="dialog" aria-labelledby="todo_modal_title">
    <div class="modal-dialog">
        <div class="modal-content">
            <div class="modal-header">
                <button type="button" class="close"
                        data-dismiss="modal" aria-label="关闭"></button>
                <h4 class="modal-title" id="todo_modal_title">添加待办</h4>
            </div>

            <form id="todo_edit_form" novalidate>
                <div class="modal-body">
                    <p id="todo_form_error" class="text-danger" role="alert"></p>

                    <div class="form-group">
                        <label for="todo_id">记录编号</label>
                        <input type="text" id="todo_id" class="form-control"
                               readonly placeholder="新增时由数据库自动生成">
                    </div>

                    <div class="form-group">
                        <label for="todo_title">待办标题（必填）</label>
                        <input type="text" id="todo_title" class="form-control"
                               maxlength="255" required
                               aria-describedby="todo_title_error">
                        <span id="todo_title_error"
                              class="help-block field-error" aria-live="polite"
                              style="display:none"></span>
                    </div>

                    <div class="form-group">
                        <label for="todo_time">创建时间（必填）</label>
                        <input type="text" id="todo_time" class="form-control"
                               required placeholder="例如 2026-11-03 14:30:00"
                               aria-describedby="todo_time_hint todo_time_error">
                        <span id="todo_time_hint" class="help-block">
                            格式：yyyy-MM-dd HH:mm:ss
                        </span>
                        <span id="todo_time_error"
                              class="help-block field-error" aria-live="polite"
                              style="display:none"></span>
                    </div>
                </div>

                <div class="modal-footer">
                    <button type="button" class="btn default"
                            data-dismiss="modal">取消</button>
                    <button type="submit" id="todo_save" class="btn blue">保存</button>
                </div>
            </form>
        </div>
    </div>
</div>
