package com.ngockhanh.clinic.healthexamination.api.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ngockhanh.clinic.healthexamination.application.usecase.*;
import com.ngockhanh.clinic.shared.web.GlobalExceptionHandler;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class OrganizationBatchControllerTest {
  @Test
  void createPassesLocalActorAndOnlyEnteredPriceAndRejectsBadInput() throws Exception {
    var create = mock(CreateHealthExaminationBatchUseCase.class);
    UUID actor = UUID.randomUUID(), org = UUID.randomUUID(), service = UUID.randomUUID();
    var env = new MockEnvironment();
    env.setActiveProfiles("local");
    env.setProperty("clinic.health-examination.batch.mock-created-by", actor.toString());
    var controller =
        new OrganizationBatchController(
            create,
            mock(GetHealthExaminationBatchUseCase.class),
            mock(ListHealthExaminationBatchUseCase.class),
            mock(UpdateHealthExaminationBatchUseCase.class),
            mock(DeleteHealthExaminationBatchUseCase.class),
            env);
    var mvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    String path = "/api/v1/organizations/" + org + "/health-examination-batches";
    String body =
        """
        {"batchCode":"B1","batchName":"Campaign","examinationSiteType":"CLINIC","examinationSiteName":"Clinic",
         "services":[{"serviceId":"%s","negotiatedUnitPrice":12.34}]}
        """
            .formatted(service);
    mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated());
    var command =
        ArgumentCaptor.forClass(
            com.ngockhanh.clinic.healthexamination.application.command
                .CreateHealthExaminationBatchCommand.class);
    verify(create).execute(eq(org), command.capture());
    assertThat(command.getValue().createdBy()).isEqualTo(actor);
    assertThat(command.getValue().configuration().services().getFirst().negotiatedUnitPrice())
        .isEqualByComparingTo("12.34");
    mvc.perform(
            post(path).contentType(MediaType.APPLICATION_JSON).content(body.replace("12.34", "-1")))
        .andExpect(status().isBadRequest());
    mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{"))
        .andExpect(status().isBadRequest());
    mvc.perform(get(path + "/invalid-uuid")).andExpect(status().isBadRequest());
    mvc.perform(get(path).param("page", "0")).andExpect(status().isBadRequest());
    mvc.perform(get(path).param("sortKey", "id; drop table services"))
        .andExpect(status().isBadRequest());
    verify(create, times(1)).execute(any(), any());
  }

  @Test
  void rejectsInvalidTransportBeforeCallingUseCases() throws Exception {
    var create = mock(CreateHealthExaminationBatchUseCase.class);
    var env = new MockEnvironment();
    env.setActiveProfiles("local");
    env.setProperty(
        "clinic.health-examination.batch.mock-created-by", UUID.randomUUID().toString());
    var controller =
        new OrganizationBatchController(
            create,
            mock(GetHealthExaminationBatchUseCase.class),
            mock(ListHealthExaminationBatchUseCase.class),
            mock(UpdateHealthExaminationBatchUseCase.class),
            mock(DeleteHealthExaminationBatchUseCase.class),
            env);
    var mvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    mvc.perform(
            post("/api/v1/organizations/" + UUID.randomUUID() + "/health-examination-batches")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.result").value("NG"));
    verifyNoInteractions(create);
  }

  @Test
  void mockActorIsDeniedOutsideLocalAndTest() {
    var create = mock(CreateHealthExaminationBatchUseCase.class);
    var env =
        new MockEnvironment()
            .withProperty(
                "clinic.health-examination.batch.mock-created-by", UUID.randomUUID().toString());
    env.setActiveProfiles("production");
    var controller =
        new OrganizationBatchController(
            create,
            mock(GetHealthExaminationBatchUseCase.class),
            mock(ListHealthExaminationBatchUseCase.class),
            mock(UpdateHealthExaminationBatchUseCase.class),
            mock(DeleteHealthExaminationBatchUseCase.class),
            env);
    assertThatThrownBy(() -> controller.delete(UUID.randomUUID(), UUID.randomUUID(), null))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    verifyNoInteractions(create);
  }
}
