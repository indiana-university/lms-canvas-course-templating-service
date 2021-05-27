package edu.iu.uits.lms.coursetemplating;

import canvas.client.generated.api.ContentMigrationApi;
import canvas.client.generated.model.ContentMigration;
import iuonly.client.generated.api.CourseTemplatingApi;
import iuonly.client.generated.model.ContentMigrationStatus;
import iuonly.client.generated.model.TemplatedCourse;
import iuonly.config.iuonly.helpers.ContentMigrationHelper;
import iuonly.coursetemplating.CourseTemplatingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CourseTemplatingServiceImpl implements CourseTemplatingService {

    @Autowired
    private ContentMigrationApi contentMigrationApi;

    @Autowired
    private CourseTemplatingApi courseTemplatingApi;

    @Override
   public void checkAndDoImsCcImport(String courseId, String termId, String accountId, String sisCourseId, String templateUrl, boolean forceApply) {
      TemplatedCourse templatedCourse = courseTemplatingApi.getTemplatedCourse(courseId);
      if (templatedCourse != null) {
         // Make sure the status is current before continuing
         updateMigrationStatusForCourse(templatedCourse);
      }

      // Only need to try and run a migration (applying template) if it has not ever been done for the course,
      // or if the previous attempt was an error
      // Or if the forceApply flag is set
      if (templatedCourse == null || ContentMigrationHelper.STATUS.ERROR.name().equals(templatedCourse.getStatus()) || forceApply) {
         log.info("Applying template to course " + courseId + " (" + sisCourseId + ")");
         ContentMigration cm = contentMigrationApi.importCCIntoCourse(courseId, null, templateUrl);
         ContentMigrationStatus cms = new ContentMigrationStatus().contentMigrationId(cm.getId())
               .status(ContentMigrationHelper.translateStatus(cm.getWorkflowState()).name());

         if (templatedCourse == null) {
            templatedCourse = new TemplatedCourse().courseId(courseId).sisCourseId(sisCourseId).termId(termId).status(ContentMigrationHelper.STATUS.PENDING.name());
         } else {
            templatedCourse.setStatus(ContentMigrationHelper.STATUS.PENDING.name());
         }
         templatedCourse.addContentMigrationsItem(cms);
         courseTemplatingApi.saveTemplatedCourse(templatedCourse);
      } else {
         log.info("Not applying template to course " + courseId + " (" + sisCourseId + ") because a template has previously been applied.");
      }
   }

   @Override
   public void updateMigrationStatusForCourses(List<TemplatedCourse> templatedCourses) {
      templatedCourses.forEach(this::updateMigrationStatusForCourse);
   }

   @Override
   public void updateMigrationStatusForCourse(TemplatedCourse templatedCourse) {
      // Get all migration statuses for the canvas course
      List<ContentMigration> migrationStatuses = contentMigrationApi.getMigrationStatuses(templatedCourse.getCourseId(), null);

      // Turn into a map so each individual one can be accessed
      Map<String, ContentMigration> statusMap = migrationStatuses.stream().collect(Collectors.toMap(ContentMigration::getId, status -> status, (a, b) -> b));

      List<ContentMigrationStatus> contentMigrationStatuses = templatedCourse.getContentMigrations();

      // if there are no migration statuses, don't bother doing any of this stuff since it will likely throw an error
      if (contentMigrationStatuses.size() > 0) {
         // Only care about the PENDING ones
         List<ContentMigrationStatus> filteredStatuses = contentMigrationStatuses.stream()
                 .filter(cms -> cms.getStatus().equals(ContentMigrationHelper.STATUS.PENDING.name()))
                 .collect(Collectors.toList());

         boolean saveTemplatedCourse = false;
         for (ContentMigrationStatus status : filteredStatuses) {
            ContentMigration canvasContentMigration = statusMap.get(status.getContentMigrationId());
            if (canvasContentMigration != null) {
               // Get our internal status value from the canvas value
               ContentMigrationHelper.STATUS translatedCanvasStatus = ContentMigrationHelper.translateStatus(canvasContentMigration.getWorkflowState());
               status.setStatus(translatedCanvasStatus.name());
               saveTemplatedCourse = true;
            }
         }

         // if the id from the canvasContentMigration doesn't map, don't bother slowing things down with saves
         // this is likely only a scenario on test environments, but slows things down quite a bit!
         if (saveTemplatedCourse) {
            // Set the status on the templatedCourse to match the last one in the list (most recent attempt)
            templatedCourse.setStatus(contentMigrationStatuses.get(contentMigrationStatuses.size() - 1).getStatus());
            courseTemplatingApi.saveTemplatedCourse(templatedCourse);
         }
      }
   }

}
