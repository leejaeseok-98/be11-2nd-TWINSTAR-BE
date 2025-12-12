package com.TwinStar.TwinStar.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

@Aspect
@Component
public class TimeTraceAop {
    @Around("@within(org.springframework.stereotype.Service) || @within(org.springframework.web.bind.annotation.RestController) || @within(org.springframework.stereotype.Controller)")
    public Object execute(ProceedingJoinPoint joinPoint) throws Throwable{
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        try {
            return joinPoint.proceed();
        } finally {
            stopWatch.stop();
            long totalTime = stopWatch.getTotalTimeMillis();

            String className = joinPoint.getTarget().getClass().getSimpleName();
            String methodName = joinPoint.getSignature().getName();

            System.out.printf("[AOP] %s.%s | %d ms\n", className, methodName, totalTime);
        }
    }
}