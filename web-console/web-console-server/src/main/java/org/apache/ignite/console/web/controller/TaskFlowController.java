package org.apache.ignite.console.web.controller;

import java.util.Collection;
import java.util.UUID;
import io.swagger.annotations.ApiOperation;
import org.apache.ignite.console.dto.Account;
import org.apache.ignite.console.dto.TaskFlow;
import org.apache.ignite.console.repositories.TaskFlowRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * Controller for taskFlows API.
 */
@RestController
@RequestMapping(path = "/api/v1/taskflow")
public class TaskFlowController {
    /** */
    private final TaskFlowRepository taskFlowsSrv;

    /**
     * @param taskFlowsSrv Notebooks service.
     */
    @Autowired
    public TaskFlowController(TaskFlowRepository taskFlowsSrv) {
        this.taskFlowsSrv = taskFlowsSrv;
    }
    
    /**
     * @param acc Account.
     * @param flowId Notebook ID.
     */
    @ApiOperation(value = "get user's taskflow.")
    @GetMapping(path = "/{taskflowId}")
    public ResponseEntity<TaskFlow> get(
        @AuthenticationPrincipal Account acc,
        @PathVariable("taskflowId") UUID taskflowId
    ) {
    	TaskFlow dto = taskFlowsSrv.get(acc.getId(), taskflowId);
    	
        return ResponseEntity.ok(dto);
    }

    /**
     * @param acc Account.
     * @return Collection of taskFlows.
     */
    @ApiOperation(value = "Get user's grouped taskFlows.")
    @GetMapping(path = "/group/{groupId}")
    public ResponseEntity<Collection<TaskFlow>> list(@AuthenticationPrincipal Account acc,
    		@PathVariable("groupId") String groupId,String action, String source) {
        return ResponseEntity.ok(taskFlowsSrv.taskFlowForGroup(acc.getId(),groupId,action, source));
    }

    /**
     * @param acc Account.
     */
    @ApiOperation(value = "Save user's flow.")
    @PutMapping(consumes = APPLICATION_JSON_VALUE)
    public ResponseEntity<UUID> save(@AuthenticationPrincipal Account acc, @RequestBody TaskFlow flow) {
        if(flow.getId()==null) {
        	flow.setId(UUID.randomUUID());
        	flow.setAccountId(acc.getId());
        }
        if(flow.getAccountId()==null) {        	
        	flow.setAccountId(acc.getId());
        }
    	taskFlowsSrv.save(acc.getId(), flow);

        return ResponseEntity.ok().body(flow.getId());
    }
    
    /**
     * @param acc Account.
     * @param flowId Notebook ID.
     */
    @ApiOperation(value = "Delete user's grouped flow.")
    @DeleteMapping(path = "/group/{groupId}")
    public ResponseEntity<Void> delete(
        @AuthenticationPrincipal Account acc,
        @PathVariable("groupId") String grpId
    ) {
        taskFlowsSrv.delete(acc.getId(), grpId);

        return ResponseEntity.ok().build();
    }

    /**
     * @param acc Account.
     * @param flowId Notebook ID.
     */
    @ApiOperation(value = "Delete user's flow.")
    @DeleteMapping(path = "/{flowId}")
    public ResponseEntity<Void> delete(
        @AuthenticationPrincipal Account acc,
        @PathVariable("flowId") UUID flowId
    ) {
        taskFlowsSrv.delete(acc.getId(), flowId);

        return ResponseEntity.ok().build();
    }
}
