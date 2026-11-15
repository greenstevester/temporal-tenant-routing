package example.tenants;

import java.util.List;
import java.util.concurrent.TimeoutException;

import com.jayway.jsonpath.JsonPath;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.enums.v1.EventType;
import io.temporal.api.enums.v1.RetryState;
import io.temporal.client.WorkflowFailedException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "TENANT_A_PASSWORD=only-for-tests-alice", "TENANT_B_PASSWORD=only-for-tests-bob",
    "TENANT_A_TARGET=127.0.0.1:7233", "TENANT_B_TARGET=127.0.0.1:7233",
    "TENANT_A_NAMESPACE=tenant-a", "TENANT_B_NAMESPACE=tenant-b", "LOCAL_TEMPORAL=true",
})
@ActiveProfiles("app")
class WebSecurityTest {
    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private JobService jobs;

    private MockMvc web;

    @BeforeEach
    void setup() {
        web = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void shouldRejectUnauthenticatedStatusRequest() throws Exception {
        web.perform(get("/jobs/job-1")).andExpect(status().isUnauthorized());
        verifyNoInteractions(jobs);
    }

    @Test
    void shouldIgnoreForgedTenantHeaderAndUseAuthenticatedTenant() throws Exception {
        when(jobs.start(anyString(), anyString())).thenReturn(
            new JobService.StartedJob("tenant-b/job/job-1", "tenant-b", "tenant-b-work"));
        web.perform(post("/jobs").with(httpBasic("bob", "only-for-tests-bob")).with(csrf())
            .header("X-Tenant-Id", "tenant-a").contentType("application/json")
            .content("{\"jobId\":\"job-1\"}"))
            .andExpect(status().isAccepted());
        verify(jobs).start("tenant-b", "job-1");
    }

    @Test
    void shouldRequireBrowserRequestForgeryTokenForJobStart() throws Exception {
        web.perform(post("/jobs").with(httpBasic("alice", "only-for-tests-alice"))
            .contentType("application/json").content("{\"jobId\":\"job-1\"}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(jobs);
    }

    @Test
    void shouldScopeStatusToAuthenticatedTenant() throws Exception {
        when(jobs.status("tenant-b", "same-id")).thenReturn("WORKFLOW_EXECUTION_STATUS_RUNNING");
        web.perform(get("/jobs/same-id").with(httpBasic("bob", "only-for-tests-bob")))
            .andExpect(status().isOk());
        verify(jobs).status("tenant-b", "same-id");
    }

    @Test
    void shouldRejectInvalidLogin() throws Exception {
        web.perform(get("/jobs/job-1").with(httpBasic("alice", "wrong")))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(jobs);
    }

    @Test
    void shouldAcceptRealRequestForgeryTokenAndCookieFromTokenEndpointWithoutServerSession()
            throws Exception {
        var response = web.perform(get("/csrf").with(httpBasic("alice", "only-for-tests-alice")))
            .andExpect(status().isOk()).andReturn();
        String json = response.getResponse().getContentAsString();
        String token = JsonPath.read(json, "$.token");
        String header = JsonPath.read(json, "$.headerName");
        Cookie tokenCookie = response.getResponse().getCookie("XSRF-TOKEN");
        assertThat(response.getRequest().getSession(false)).isNull();
        assertThat(tokenCookie).isNotNull();
        when(jobs.start("tenant-a", "job-1")).thenReturn(
            new JobService.StartedJob("tenant-a/job/job-1", "tenant-a", "tenant-a-work"));

        web.perform(post("/jobs").with(httpBasic("alice", "only-for-tests-alice"))
            .cookie(tokenCookie).header(header, token).contentType("application/json")
            .content("{\"jobId\":\"job-1\"}")).andExpect(status().isAccepted());
        verify(jobs).start("tenant-a", "job-1");
    }

    @Test
    void shouldReturnWaitingResponseWhenWorkflowHasNotCompleted() throws Exception {
        when(jobs.result("tenant-b", "job-1")).thenThrow(new TimeoutException("still waiting"));
        web.perform(get("/jobs/job-1/result").with(httpBasic("bob", "only-for-tests-bob")))
            .andExpect(status().isAccepted());
    }

    @Test
    void shouldReturnConflictWithoutInternalDetailsWhenWorkflowFailed() throws Exception {
        when(jobs.result("tenant-b", "job-1")).thenThrow(new WorkflowFailedException(
            WorkflowExecution.newBuilder().setWorkflowId("tenant-b/job/job-1").build(),
            "JobWorkflow", EventType.EVENT_TYPE_WORKFLOW_EXECUTION_FAILED, 0,
            RetryState.RETRY_STATE_NON_RETRYABLE_FAILURE, new RuntimeException("internal detail")));
        web.perform(get("/jobs/job-1/result").with(httpBasic("bob", "only-for-tests-bob")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("Job did not complete"))
            .andExpect(content().string(not(containsString("internal detail"))));
    }

    @Test
    void shouldReturnResultForAuthenticatedTenant() throws Exception {
        when(jobs.result("tenant-b", "job-1")).thenReturn(new JobResult("tenant-b", List.of()));
        web.perform(get("/jobs/job-1/result").with(httpBasic("bob", "only-for-tests-bob")))
            .andExpect(status().isOk());
        verify(jobs).result("tenant-b", "job-1");
    }

    @Test
    void shouldScopeWorkerHistoryToAuthenticatedTenant() throws Exception {
        when(jobs.workflowWorkers("tenant-b", "job-1")).thenReturn(List.of("tenant-b/pod-1"));
        web.perform(get("/jobs/job-1/workers").with(httpBasic("bob", "only-for-tests-bob")))
            .andExpect(status().isOk());
        verify(jobs).workflowWorkers("tenant-b", "job-1");
    }

    @Test
    void shouldRejectInvalidJobIdWithoutRevealingInternalDetails() throws Exception {
        when(jobs.start("tenant-a", "bad id")).thenThrow(new InvalidJobIdException());
        web.perform(post("/jobs").with(httpBasic("alice", "only-for-tests-alice")).with(csrf())
            .contentType("application/json").content("{\"jobId\":\"bad id\"}"))
            .andExpect(status().isBadRequest());
    }
}
