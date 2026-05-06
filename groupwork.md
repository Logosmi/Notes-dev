### 📁 一、新增包 `net.micode.notes.account`（账户模块）

| 文件 | 类型 | 职责概述 |
|------|------|----------|
| `User.java` | `<entity>` | 用户实体，属性：`_id`, `username`, `passwordHash`, `securityQuestion`, `securityAnswerHash`, `failCount`, `createdTime`；提供 getter/setter 及 `incrementFailCount()`, `resetFailCount()` |
| `LocalAccountManager.java` | `<control>` | 账户业务逻辑：注册、登录、密码找回、密码哈希、复杂度校验、登录状态管理 |

---

### 📁 二、新增包 `net.micode.notes.tag`（标签模块）

| 文件 | 类型 | 职责概述 |
|------|------|----------|
| `Tag.java` | `<entity>` | 标签实体，属性：`_id`, `name`, `color`, `createdTime` |
| `NoteTag.java` | `<entity>` | 便签-标签关联实体，属性：`noteId`, `tagId` |
| `TagManager.java` | `<control>` | 标签管理逻辑：创建、删除、关联、查询标签，按标签筛选便签 |

---

### 📁 三、新增包 `net.micode.notes.image`（图片模块）

| 文件 | 类型 | 职责概述 |
|------|------|----------|
| `Image.java` | `<entity>` | 图片实体，属性：`_id`, `noteId`, `localFilePath`, `fileName`, `fileSize`, `width`, `height`, `mimeType`, `createdTime` |
| `ImageManager.java` | `<control>` | 图片管理逻辑：插入、删除、导出、格式验证、压缩、私有存储读写、权限检查 |

---

### 📁 四、扩展包 `net.micode.notes.ui`（边界类）

| 文件 | 类型 | 职责概述 |
|------|------|----------|
| `LoginActivity.java` | `<boundary>` | 登录界面，调用 `LocalAccountManager.login()` |
| `RegistrationActivity.java` | `<boundary>` | 注册界面，调用 `LocalAccountManager.register()` |
| `ChangeSecurityQuestionActivity.java` | `<boundary>` | 改变安全问题，调用 ' LocalAccountManager.changeSecurityQuestion()` |
| `ForgotPasswordActivity.java` | `<boundary>` | 忘记密码界面，调用 `LocalAccountManager` 验证问题和重置密码 |
| `TagManagementActivity.java` | `<boundary>` | 标签管理列表，调用 `TagManager` 增删标签 |
| `TagSelectionDialog.java` | `<boundary>` | 为便签选择标签的对话框，调用 `TagManager` 获取标签列表和关联标签 |

> 以上均注册在 `AndroidManifest.xml` 中，并保持与现有 Activity 的跳转关系。

---

### 📁 五、修改的现有文件（`net.micode.notes.data` 包内）

| 文件 | 修改类型 | 具体扩展 |
|------|---------|---------|
| `NotesDatabaseHelper.java` | 新增常量与方法 | 增加 `TABLE_USER`, `TABLE_TAG`, `TABLE_NOTE_TAG`, `TABLE_IMAGE` 四个表名的常量；在 `onCreate()` 中调用新建表方法（`createUserTable`, `createTagTable`, `createNoteTagTable`, `createImageTable`）；在 `onUpgrade()` 中添加新表迁徙逻辑 |
| `NotesProvider.java` | 新增 URI 与处理分支 | 增加四个 URI 匹配常量：`URI_USER`, `URI_TAG`, `URI_NOTE_TAG`, `URI_IMAGE`；在 `query()`, `insert()`, `update()`, `delete()` 中增加对这四个 URI 的case，委托给相应表的 CRUD 操作 |

---

### 📁 六、目录结构一览

```
app/src/main/java/net/micode/notes/
├── account/
│   ├── User.java
│   └── LocalAccountManager.java
├── tag/
│   ├── Tag.java
│   ├── NoteTag.java
│   └── TagManager.java
├── image/
│   ├── Image.java
│   └── ImageManager.java
├── ui/
│   ├── (现有 Activity 不变)
│   ├── ChangeSecurityQuestionActivity.java
│   ├── LoginActivity.java
│   ├── RegistrationActivity.java
│   ├── ForgotPasswordActivity.java
│   ├── TagManagementActivity.java
│   └── TagSelectionDialog.java
├── data/
│   ├── NotesDatabaseHelper.java  (修改)
│   └── NotesProvider.java        (修改)
└── (其他原有包保持不变)
```

---

### 📁 七、融入现有项目的方式

1. **依赖关系**  
   - `ui` 包中的新 Activity 通过引入对应包的控制类来执行逻辑（如 `LocalAccountManager`, `TagManager`, `ImageManager`），保持界面与业务分离。
   - 控制类通过 `NotesProvider` 或 `SQLiteDatabase` 操作实体表，完全复用现有数据访问层。

2. **启动流程调整**  
   `AndroidManifest.xml` 中先将 `LoginActivity` 设置为启动页，原 `NotesListActivity` 等待登录成功后才启动。

3. **权限声明**  
   在清单文件中添加 `CAMERA`, `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE` 权限（若之前未添加），并在 `ImageManager` 中完成运行时申请。

4. **数据库版本升级**  
   提升 `DATABASE_VERSION`，在 `onUpgrade()` 中根据旧版本判断是否添加新表，确保旧用户数据不丢失。






## 👥 三人分工方案

| 成员 | 负责模块 | 新建/修改的文件 | 关键依赖 |
|------|----------|-----------------|----------|
| **成员 A**（账户） | `account` 包 + 相关 UI + 数据库用户表 | `account/User.java`、`account/LocalAccountManager.java`、`ui/LoginActivity.java`、`ui/RegistrationActivity.java`、`ui/ForgotPasswordActivity.java`；**修改 `AndroidManifest.xml` 注册新 Activity** | 需要使用 `NotesDatabaseHelper` 新增 `user` 表；需要通过 `NotesProvider` 操作用户数据 |
| **成员 B**（标签） | `tag` 包 + 标签管理 UI + 列表筛选改造 | `tag/Tag.java`、`tag/NoteTag.java`、`tag/TagManager.java`、`ui/TagManagementActivity.java`、`ui/TagSelectionDialog.java`；**修改 `NotesListActivity` 增加标签筛选栏** | 需要使用 `NotesDatabaseHelper` 新增 `tag`、`note_tag` 表；需要通过 `NotesProvider` 操作标签数据；需在 `NoteEditActivity` 中增加“添加标签”入口（可先预留） |
| **成员 C**（图片） | `image` 包 + 编辑页图片功能改造 | `image/Image.java`、`image/ImageManager.java`；**修改 `NoteEditActivity`**（增加插入/删除/导出图片按钮、缩略图展示）、**修改 `AndroidManifest.xml` 添加权限** | 需要使用 `NotesDatabaseHelper` 新增 `image` 表；需要通过 `NotesProvider` 操作图片数据；需要相机/存储权限处理 |

- [x]
**公共基础设施**（建议由组长或一名成员提前完成，可分配给成员 A，因为账户表最简单）：
- 修改 `NotesDatabaseHelper.java`：增加 `TABLE_USER`、`TABLE_TAG`、`TABLE_NOTE_TAG`、`TABLE_IMAGE` 四个表的建表语句，升级 `DATABASE_VERSION`。
- 修改 `NotesProvider.java`：增加四个 URI 匹配常量及对应的 `case` 分支。
- 在 `AndroidManifest.xml` 中添加 `CAMERA`、`READ_EXTERNAL_STORAGE` 等权限（成员 C 可后续补充）。

---

## 📐 协作流程与接口约定

### 1. 数据库与 Provider 的统一修改（公共基础）
- 由**成员 A**（或指定组长）在共享分支上首先完成 `NotesDatabaseHelper` 和 `NotesProvider` 的改造，并提交。
- 修改完成后，其他成员拉取该分支，各自在独立分支上开发功能模块。

### 2. 实体与控制类并行开发
- 成员 A、B、C 分别在各自包内创建实体类和管理类，互不干扰。
- 控制类需遵循统一的数据库访问方式：
  - 通过 `ContentResolver` 调用 `NotesProvider` 中新增的 URI（`URI_USER`、`URI_TAG`、`URI_NOTE_TAG`、`URI_IMAGE`）。
  - 不直接操作 `SQLiteDatabase`，保持数据访问一致性。

### 3. UI 界面开发约定
- **启动流程**：成员 A 负责将 `LoginActivity` 设为启动页，并在登录成功后跳转到 `NotesListActivity`。
- **标签筛选**：成员 B 负责在 `NotesListActivity` 的布局中增加标签筛选区域（如 `ChipGroup`），并调用 `TagManager` 完成筛选逻辑。
- **图片功能**：成员 C 负责在 `NoteEditActivity` 中增加图片相关按钮和缩略图容器，调用 `ImageManager` 处理图片。

为了避免 `NotesListActivity.java` 和 `NoteEditActivity.java` 冲突：
- 成员 B 和成员 C 在修改现有 Activity 前，需沟通好各自新增的**控件 ID** 和**菜单项 ID**。
- 例如：
  - 成员 B 在 `NotesListActivity` 中新增 `tag_filter_container`。
  - 成员 C 在 `NoteEditActivity` 中新增 `insert_image_btn`、`image_container`。
- 如果两人都需要修改同一文件，可约定好修改行数范围，最后手动合并。

### 4. 资源文件约定
- 字符串资源（`strings.xml`）：每个模块新增的字符串统一添加在文件末尾，并添加注释区分（如 `<!-- Account -->`、`<!-- Tag -->`、`<!-- Image -->`）。
- 布局文件：新建布局用独立的 `activity_xxx.xml`，避免修改已有布局文件造成冲突。

### 5. 权限声明
- 成员 C 负责在 `AndroidManifest.xml` 中补充 `CAMERA`、`READ_EXTERNAL_STORAGE` 权限，并在运行时申请。
- 其他权限如 `SCHEDULE_EXACT_ALARM` 等保留不变。

---

## 🧪 集成与测试计划

1. 各成员在独立分支开发并自测通过后，合并到 `dev` 分支。
2. 集成测试重点：
   - 注册 → 登录 → 进入主界面（成员 A）。
   - 创建标签 → 为便签添加标签 → 按标签筛选（成员 B）。
   - 插入图片 → 保存 → 再次编辑可见 → 删除图片 → 导出图片（成员 C）。
   - 三个功能同时使用时，原有便签功能不受影响。

3. 有任何数据库结构或接口变动，需立即同步到小组群内，避免其他成员无法编译。
