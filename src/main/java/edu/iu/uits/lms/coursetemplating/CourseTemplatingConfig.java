package edu.iu.uits.lms.coursetemplating;

import canvas.config.EnableCanvasClient;
import iuonly.config.EnableIuOnlyClient;
import iuonly.coursetemplating.CourseTemplatingService;
import org.springframework.context.annotation.Bean;

@EnableIuOnlyClient
@EnableCanvasClient
public class CourseTemplatingConfig {

   @Bean
   public CourseTemplatingService courseTemplatingService(){
      return new CourseTemplatingServiceImpl();
   }
}
