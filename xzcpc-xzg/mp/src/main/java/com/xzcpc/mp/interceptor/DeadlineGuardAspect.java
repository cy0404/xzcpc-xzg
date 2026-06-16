package com.xzcpc.mp.interceptor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.task.entity.Task;
import com.xzcpc.task.mapper.TaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DeadlineGuardAspect {

    private final TaskMapper taskMapper;

    @Before("execution(* com.xzcpc.mp.controller..*(..))")
    public void checkDeadline(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        org.springframework.web.bind.annotation.RequestMapping reqMapping =
                method.getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class);
        if (reqMapping != null && isReadOnly(reqMapping.method())) {
            return;
        }

        org.springframework.web.bind.annotation.GetMapping getMapping =
                method.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class);
        if (getMapping != null) return;

        org.springframework.web.bind.annotation.PostMapping postMapping =
                method.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class);
        org.springframework.web.bind.annotation.PutMapping putMapping =
                method.getAnnotation(org.springframework.web.bind.annotation.PutMapping.class);
        org.springframework.web.bind.annotation.DeleteMapping deleteMapping =
                method.getAnnotation(org.springframework.web.bind.annotation.DeleteMapping.class);
        org.springframework.web.bind.annotation.PatchMapping patchMapping =
                method.getAnnotation(org.springframework.web.bind.annotation.PatchMapping.class);

        if (postMapping == null && putMapping == null && deleteMapping == null && patchMapping == null
                && reqMapping == null) {
            return;
        }

        Object[] args = joinPoint.getArgs();
        for (Object arg : args) {
            if (arg instanceof Integer) {
                Integer taskId = (Integer) arg;
                Task task = taskMapper.selectById(taskId);
                if (task != null && task.getDeadline() != null
                        && task.getDeadline().isBefore(LocalDateTime.now())) {
                    throw new BusinessException(4033, "任务已过截止时间");
                }
                return;
            }
        }
    }

    private boolean isReadOnly(RequestMethod[] methods) {
        if (methods == null || methods.length == 0) return false;
        for (RequestMethod m : methods) {
            if (m != RequestMethod.GET) return false;
        }
        return true;
    }
}
