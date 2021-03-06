package org.camunda.bpm.engine.test.api.runtime;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Date;
import java.util.List;

import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.batch.Batch;
import org.camunda.bpm.engine.batch.history.HistoricBatch;
import org.camunda.bpm.engine.history.HistoricJobLog;
import org.camunda.bpm.engine.impl.batch.BatchMonitorJobHandler;
import org.camunda.bpm.engine.impl.batch.BatchSeedJobHandler;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.management.JobDefinition;
import org.camunda.bpm.engine.runtime.Job;
import org.camunda.bpm.engine.test.ProcessEngineRule;

public abstract class BatchHelper {

  protected ProcessEngineRule engineRule;

  public BatchHelper(ProcessEngineRule engineRule) {
    this.engineRule = engineRule;
  }

  public Job getJobForDefinition(JobDefinition jobDefinition) {
    if (jobDefinition != null) {
      return engineRule.getManagementService()
        .createJobQuery().jobDefinitionId(jobDefinition.getId()).singleResult();
    }
    else {
      return null;
    }
  }

  public List<Job> getJobsForDefinition(JobDefinition jobDefinition) {
    return engineRule.getManagementService()
      .createJobQuery().jobDefinitionId(jobDefinition.getId()).list();
  }

  public void executeJob(Job job) {
    assertNotNull("Job to execute does not exist", job);
    try {
      engineRule.getManagementService().executeJob(job.getId());
    }
    catch (Exception e) {
      // ignore
    }
  }

  public JobDefinition getSeedJobDefinition(Batch batch) {
    return engineRule.getManagementService()
      .createJobDefinitionQuery()
      .jobDefinitionId(batch.getSeedJobDefinitionId())
      .jobType(BatchSeedJobHandler.TYPE)
      .singleResult();
  }

  public Job getSeedJob(Batch batch) {
    return getJobForDefinition(getSeedJobDefinition(batch));
  }

  public void executeSeedJob(Batch batch) {
    executeJob(getSeedJob(batch));
  }

  public JobDefinition getMonitorJobDefinition(Batch batch) {
    return engineRule.getManagementService()
      .createJobDefinitionQuery().jobDefinitionId(batch.getMonitorJobDefinitionId()).jobType(BatchMonitorJobHandler.TYPE).singleResult();
  }

  public Job getMonitorJob(Batch batch) {
    return getJobForDefinition(getMonitorJobDefinition(batch));
  }

  public void executeMonitorJob(Batch batch) {
    executeJob(getMonitorJob(batch));
  }

  public void completeMonitorJobs(Batch batch) {
    while (getMonitorJob(batch) != null) {
      executeMonitorJob(batch);
    }
  }

  public void completeSeedJobs(Batch batch) {
    while (getSeedJob(batch) != null) {
      executeSeedJob(batch);
    }
  }

  public abstract JobDefinition getExecutionJobDefinition(Batch batch);

  public List<Job> getExecutionJobs(Batch batch) {
    return getJobsForDefinition(getExecutionJobDefinition(batch));
  }

  public void executeJobs(Batch batch) {
    for (Job job : getExecutionJobs(batch)) {
      executeJob(job);
    }
  }

  public void completeBatch(Batch batch) {
    completeSeedJobs(batch);
    completeExecutionJobs(batch);
    completeMonitorJobs(batch);
  }

  public void completeJobs(Batch batch, int count) {
    List<Job> jobs = getExecutionJobs(batch);
    assertTrue(jobs.size() >= count);
    for (int i = 0; i < count; i++) {
      executeJob(jobs.get(i));
    }
  }

  public void failExecutionJobs(Batch batch, int count) {
    setRetries(batch, count, 0);
  }

  public void setRetries(Batch batch, int count, int retries) {
    List<Job> jobs = getExecutionJobs(batch);
    assertTrue(jobs.size() >= count);

    ManagementService managementService = engineRule.getManagementService();
    for (int i = 0; i < count; i++) {
      managementService.setJobRetries(jobs.get(i).getId(), retries);
    }

  }

  public void completeExecutionJobs(Batch batch) {
    while (!getExecutionJobs(batch).isEmpty()) {
      executeJobs(batch);
    }
  }
  public HistoricBatch getHistoricBatch(Batch batch) {
    return engineRule.getHistoryService()
      .createHistoricBatchQuery()
      .batchId(batch.getId())
      .singleResult();
  }

  public List<HistoricJobLog> getHistoricSeedJobLog(Batch batch) {
    return engineRule.getHistoryService()
      .createHistoricJobLogQuery()
      .jobDefinitionId(batch.getSeedJobDefinitionId())
      .orderPartiallyByOccurrence()
      .asc()
      .list();
  }

  public List<HistoricJobLog> getHistoricMonitorJobLog(Batch batch) {
    return engineRule.getHistoryService()
      .createHistoricJobLogQuery()
      .jobDefinitionId(batch.getMonitorJobDefinitionId())
      .orderPartiallyByOccurrence()
      .asc()
      .list();
  }

  public List<HistoricJobLog> getHistoricMonitorJobLog(Batch batch, Job monitorJob) {
    return engineRule.getHistoryService()
      .createHistoricJobLogQuery()
      .jobDefinitionId(batch.getMonitorJobDefinitionId())
      .jobId(monitorJob.getId())
      .orderPartiallyByOccurrence()
      .asc()
      .list();
  }

  public List<HistoricJobLog> getHistoricBatchJobLog(Batch batch) {
    return engineRule.getHistoryService()
      .createHistoricJobLogQuery()
      .jobDefinitionId(batch.getBatchJobDefinitionId())
      .orderPartiallyByOccurrence()
      .asc()
      .list();
  }

  public Date addSeconds(Date date, int seconds) {
    return new Date(date.getTime() + seconds * 1000);
  }

  public Date addSecondsToClock(int seconds) {
    Date newDate = addSeconds(ClockUtil.getCurrentTime(), seconds);
    ClockUtil.setCurrentTime(newDate);
    return newDate;
  }

  /**
   * Remove all batches and historic batches. Usually called in {@link org.junit.After} method.
   */
  public void removeAllRunningAndHistoricBatches() {
    HistoryService historyService = engineRule.getHistoryService();
    ManagementService managementService = engineRule.getManagementService();

    for (Batch batch : managementService.createBatchQuery().list()) {
      managementService.deleteBatch(batch.getId(), true);
    }

    // remove history of completed batches
    for (HistoricBatch historicBatch : historyService.createHistoricBatchQuery().list()) {
      historyService.deleteHistoricBatch(historicBatch.getId());
    }

  }

}
