## 1. Project Infrastructure and Database Setup

- [x] 1.1 Initialize Spring Boot 3 backend project with Maven/Gradle, MySQL, MyBatis-Plus, and validation dependencies
- [x] 1.2 Initialize React 18 + Vite frontend project with Ant Design, Axios, and React Router
- [x] 1.3 Design and execute MySQL schema DDL (users, roles, batches, metrics, projects, scoring records)
- [x] 1.4 Implement database seed data with default roles, initial admin, sample leaders, experts, and staff

## 2. User Authentication and Role Permissions

- [x] 2.1 Implement backend authentication controller, token issuance, and password verification
- [x] 2.2 Implement user profile and accessible roles API supporting multi-role assignments
- [x] 2.3 Implement frontend login page, auth context, and persistent token storage
- [x] 2.4 Implement frontend top-bar active role switcher with dynamic navigation menu filtering
- [x] 2.5 Implement administrator user management page (list, add, edit, toggle status)

## 3. Metric and Weight Configuration

- [x] 3.1 Implement backend metric CRUD APIs with batch association
- [x] 3.2 Implement default metric template loading for monthly appraisal and annual project appraisal
- [x] 3.3 Implement backend and frontend 100-point total weight validation before saving
- [x] 3.4 Build metric configuration UI component within appraisal batch wizard

## 4. Monthly Performance Appraisal Module

- [x] 4.1 Implement monthly appraisal batch management (create batch, configure period, assign examinees and leaders with weights)
- [x] 4.2 Implement leader scoring backend APIs (fetch assigned examinees, save draft, finalize submission)
- [x] 4.3 Build leader scoring frontend page with grade inputs, 4-decimal real-time calculation, draft, submit, and self-withdrawal
- [x] 4.4 Implement score aggregation service supporting leader weights, overdue 0-point handling, and rankings
- [x] 4.5 Implement examinee personal result view page (displaying personal total score and department rankings)
- [x] 4.6 Implement monthly batch result summary view and Excel export for administrators

## 5. Annual Project Appraisal Module

- [x] 5.1 Implement project candidate management (CRUD, assign project owner, upload material and attachment PDFs)
- [x] 5.2 Implement secure local PDF storage and authenticated streaming preview endpoint
- [x] 5.3 Implement annual project batch management and expert assignment
- [x] 5.4 Build split-screen expert evaluation workbench (left side PDF viewer, right side metric scoring)
- [x] 5.5 Implement expert weight configuration, self-withdrawal before deadline, and overdue 0-point handling
- [x] 5.6 Implement project score aggregation (weighted average across experts with 4 decimals) and ranking
- [x] 5.7 Implement admin-exclusive project appraisal summary page and Excel export matching prototype grid

## 6. Integration and End-to-End Verification

- [x] 6.1 Verify complete monthly appraisal flow from batch creation, leader scoring, to summary and export
- [x] 6.2 Verify complete annual project appraisal flow from PDF upload, expert scoring, to rank export
- [x] 6.3 Test multi-role user switching and access control boundaries
- [x] 6.4 Validate production build of frontend and backend packages
