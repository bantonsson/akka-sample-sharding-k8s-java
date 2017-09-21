# Akka Cluster Sharding on Kubernetes

This sample contains code that illustrates how to use [Akka Cluster Sharding](https://doc.akka.io/docs/akka/current/java/cluster-sharding.html#an-example) and how to deploy it on [Kubernetes](https://kubernetes.io/).

## Prerequisites

### Locally without Kubernetes

  * [Java 8][java8]
  * [sbt][sbt]
  * [curl][curl]

### Locally with Kubernetes on Minikube

  * [Java 8][java8]
  * [sbt][sbt]
  * [curl][curl]
  * [Docker][docker]
  * [Minikube][minikube]
  * [kubectl][k8s-kubectl]

## Sample overview

The main program [ShardingApp](src/main/java/sample/sharding/ShardingApp.java) creates and starts one or more akka actor systems, and joins them in a cluster.

In each actor system, there is a [MainActor](src/main/java/sample/sharding/MainActor.java) created that initializes the akka cluster sharding, and also starts a an HTTP server, that can be used to interact with the sharded entities.

The sharded actor entities defined in [CustomerActor](src/main/java/sample/sharding/CustomerActor.java) represent customers, and have an integer customer id that is unique. Since there is no persistent storage involved in this sample, the customer data is randomized using the customer id as a seed, to ensure that it is the same for that particular customer, but can vary between customers.

There are just two HTTP endpoints defined.

  * `/customer/<Integer>/address`: Will retrieve the address for that specific customer
  * `/stop`: Will shut down that akka system

The HTTP requests can be sent to any of the nodes in the akka cluster, and the request will be routed to the correct customer actor via the cluster sharding.

### Request diagrams

This diagram shows how a request is routed for an entity that is in a shard that hasn't been assigned to a shard region yet. The shard coordinator will select a shard region based on the `ShardAllocationStrategy` supplied when starting the cluster sharding. The default strategy picks the shard region that has the least number of shards.

```
   +------+  +-----------+   +------+    +-----------+    +------+    +------+
   |Client|  |HTTP Server|   |Shard |    |   Shard   |    |Shard |    |Entity|
   +------+  +-----------+   |Region|    |Coordinator|    |Region|    +------+
      |            |         +------+    +-----------+    +------+       |
      |            |            |              |             |           |
      |            |            |              |             |           |
      |  REQUEST   |            |              |             |           |
      |----------->|  ask(msg)  |              |             |           |
      |            |----------->| GetShardHome |             |           |
      |            |            |------------->|             |           |
      |            |            |              |             |           |
      |            |            |   ShardHome  |             |           |
      |            |            |<-------------|             |           |
      |            |            |              |             |           |
      |            |            |             msg            |           |
      |            |            |--------------------------->|    msg    |
      |            |            |              |             |---------->|
      |            |            |              |             |           |
      |            |            |            reply           |           |
      |  RESPONSE  |<----------------------------------------------------|
      |<-----------|            |              |             |           |
      |            |            |              |             |           |
```

This diagram shows how a request is routed for an entity that is in a shard that has been assigned to a shard region that is not the one receiving the HTTP request. Please note that the shard coordinator is completely bypassed in this scenario.

```
   +------+  +-----------+   +------+    +-----------+    +------+    +------+
   |Client|  |HTTP Server|   |Shard |    |   Shard   |    |Shard |    |Entity|
   +------+  +-----------+   |Region|    |Coordinator|    |Region|    +------+
      |            |         +------+    +-----------+    +------+       |
      |            |            |              |             |           |
      |            |            |              |             |           |
      |  REQUEST   |            |              |             |           |
      |----------->|  ask(msg)  |              |             |           |
      |            |----------->|             msg            |           |
      |            |            |--------------------------->|    msg    |
      |            |            |              |             |---------->|
      |            |            |              |             |           |
      |            |            |            reply           |           |
      |  RESPONSE  |<----------------------------------------------------|
      |<-----------|            |              |             |           |
      |            |            |              |             |           |
```

This diagram shows how a request is routed for an entity that is in a shard that has been assigned to the shard region that is receiving the HTTP request.

```
   +------+  +-----------+   +------+    +------+
   |Client|  |HTTP Server|   |Shard |    |Entity|
   +------+  +-----------+   |Region|    +------+
      |            |         +------+       |
      |            |            |           |
      |            |            |           |
      |  REQUEST   |            |           |
      |----------->|  ask(msg)  |           |
      |            |----------->|    msg    |
      |            |            |---------->|
      |            |            |           |
      |            |          reply         |
      |  RESPONSE  |<-----------------------|
      |<-----------|            |           |
      |            |            |           |
```

## Running locally

### Starting up the akka cluster nodes

Running the sample locally will create one or more akka actor systems that each have an HTTP server, and join them in a cluster.

```
> sbt "run 2551 8081 2552 8082 2553 8083"
...
[info] [INFO] [09/11/2017 09:09:37.791] [ShardingSample-akka.actor.default-dispatcher-2] [akka://ShardingSample/user/$a] Server online at http://localhost:8081/
[info] [INFO] [09/11/2017 09:09:37.791] [ShardingSample-akka.actor.default-dispatcher-20] [akka://ShardingSample/user/$a] Server online at http://localhost:8082/
[info] [INFO] [09/11/2017 09:09:37.791] [ShardingSample-akka.actor.default-dispatcher-16] [akka://ShardingSample/user/$a] Server online at http://localhost:8083/
```

### Interacting with the akka cluster

Now that the cluster is running you can call the REST endpoints to test it.

```
> curl http://localhost:8081/customer/4711/address
Server localhost:8081 replying:
Customer: 4711
Address: Some Street 20
Actor: akka.tcp://ShardingSample@127.0.0.1:2551/system/sharding/Customer/11/4711
```

It doesn't matter which HTTP server you query, the actor representing the customer should still be running on the same shard, on the same akka node.

```
> curl http://127.0.0.1:8082/customer/4711/address
Server localhost:8082 replying:
Customer: 4711
Address: Some Street 20
Actor: akka.tcp://ShardingSample@127.0.0.1:2551/system/sharding/Customer/11/4711
```

### Stopping akka cluster nodes

You can stop one of the akka cluster nodes and see that the customer actor will be started on a another node.

```
> curl http://127.0.0.1:8081/stop
Server localhost:8081 is shutting Down
> Server localhost:8082 replying:
Customer: 4711
Address: Some Street 20
Actor: akka.tcp://ShardingSample@127.0.0.1:2552/system/sharding/Customer/11/4711
```

## Running with Kubernetes on Minikube

### Starting up Minikube

First we need to start Minikube if it isn't running already.

```
> minikube start
```

Or if [VirtualBox][virtualbox] is not installed on the system and we are using docker with [xhyve][xhyve].

```
> minikube start --vm-driver=xhyve
```

The output should look something like this:

```
Starting local Kubernetes v1.7.5 cluster...
Starting VM...
Getting VM IP address...
Moving files into cluster...
Setting up certs...
Connecting to cluster...
Setting up kubeconfig...
Starting cluster components...
Kubectl is now configured to use the cluster.
```

Depending on how many pods you want to start, and their resource usage, you might have to change the resources allocated to Minikube by adding cpu and memory flags to the start command like this.

```
> minikube start --cpus 4 --memory 4096
```

### Building and publishing the docker image

To be able to run the application on Kubernetes, we need to build a docker image of the project. First make sure that sbt will publish the image to the docker repository inside Minikube.

```
> eval $(minikube docker-env)
```

Then publish the image.

```
> sbt docker:publishLocal
```

Near the end of the output, there should be a line looking something like this:

```
[info] Built image samplegroup/akka-sample-sharding-k8s-java:0.0.1
```

### Anatomy of running the sample on Kubernetes

To start up an Akka cluster we need to have some named _stable_ akka nodes, called seed nodes. To accomplish this with just kubernetes, we are going to use a [StatefulSet][k8s-statefulset]. The full definition of the stateful set can be found in the file [k8s/sharding-sample-seeds.yaml](k8s/sharding-sample-seeds.yaml).

In short the sharding-sample-seeds StatefulSet does 4 basic things:

  * It specifies a Service to use for the exposed stable Pods, `serviceName: seeds-remoting`.
  * It specifies which docker image to run, `image: samplegroup/akka-sample-sharding-k8s-java`.
  * It specifies which ports to open, `containerPort: 8080` and `containerPort: 2551`.
  * It defines a number of environment variables that will be used for configuring the akka system.

Then there are also normal members of the cluster. These akka nodes do not need to have stable names, but only know about the seed nodes, so they are defined in a [Deployment][k8s-deployment]. The full definition of the deployment can be found in the file [k8s/sharding-sample-members.yaml](k8s/sharding-sample-members.yaml).

The sharding-sample-members deployment does almost the same thing as the sharding-sample-seeds stateful set, except it doesn't specify a Service since it doesn't need to expose stable Pods.

And last but not least there is a [Service][k8s-service] that defines which Pods to expose the HTTP traffic for. The full definition of the service can be found in the file [k8s/sharding-sample-http.yaml](k8s/sharding-sample-http.yaml).

### Starting up the akka cluster seed nodes

Before the seed nodes can be started we need create the service that defines which pods and ports should be exposed.

```
> kubectl apply -f k8s/seeds-remoting.yaml
service "seeds-remoting" created
```

Then create the stateful set that starts the seed nodes.

```
> kubectl apply -f k8s/sharding-sample-seeds.yaml
statefulset "sharding-sample-seeds" created
```

The sharding-sample-seeds stateful set defaults to starting one pod. We can look at the status of the pods like this.

```
> kubectl get pods
NAME                      READY     STATUS    RESTARTS   AGE
sharding-sample-seeds-0   1/1       Running   0          36s
```

To look at the logs of a particular pod, we can use the `logs` command like this.

```
kubectl logs sharding-sample-seeds-0
```

### Accessing the akka cluster seed node through the Proxy

To be able to interact with the pods in the Kubernetes cluster, we should start the proxy. This should be done in a separate terminal window.

```
> kubectl proxy
Starting to serve on 127.0.0.1:8001
```

We can now access the akka cluster seed node HTTP server by doing a curl via the Kubernetes proxy.

```
> curl http://localhost:8001/api/v1/proxy/namespaces/default/pods/sharding-sample-seeds-0/customer/4711/address
Server sharding-sample-seeds-0:8080 replying:
Customer: 4711
Address: Some Street 20
Actor: akka.tcp://sharding-sample@sharding-sample-seeds-0.seeds-remoting:2551/system/sharding/Customer/11/4711
```

### Scaling the akka cluster seed nodes

Now that we have one akka seed node up and running, we might want to scale it up. There is no value in scaling the sharding-sample-seeds to more than three since the sample only uses the three first names in the seed node definition.

```
> kubectl scale statefulset sharding-sample-seeds --replicas 3
```

After a few seconds, one or two new pods should have started or are in the process of starting.

```
> kubectl get pods
NAME                      READY     STATUS    RESTARTS   AGE
sharding-sample-seeds-0   1/1       Running   0          5m
sharding-sample-seeds-1   1/1       Running   0          22s
sharding-sample-seeds-2   0/1       Running   0          <invalid>
```

And then we can check if they have joined the cluster.

```
> kubectl logs sharding-sample-seeds-0 | grep -i joining
[INFO] [09/11/2017 08:06:12.112] [sharding-sample-akka.actor.default-dispatcher-20] [akka.cluster.Cluster(akka://sharding-sample)] Cluster Node [akka.tcp://sharding-sample@sharding-sample-seeds-0.seeds-remoting:2551] - Node [akka.tcp://sharding-sample@sharding-sample-seeds-0.seeds-remoting:2551] is JOINING, roles []
[INFO] [09/11/2017 08:17:04.029] [sharding-sample-akka.actor.default-dispatcher-20] [akka.cluster.Cluster(akka://sharding-sample)] Cluster Node [akka.tcp://sharding-sample@sharding-sample-seeds-0.seeds-remoting:2551] - Node [akka.tcp://sharding-sample@sharding-sample-seeds-1.seeds-remoting:2551] is JOINING, roles []
```

Please note that the sharding-sample-seeds-2 pod might have joined either of the two akka cluster seed nodes, so we might need to check the logs of sharding-sample-seeds-1.

```
> kubectl logs sharding-sample-seeds-1 | grep -i joining
[INFO] [09/11/2017 08:17:49.604] [sharding-sample-akka.actor.default-dispatcher-15] [akka.cluster.Cluster(akka://sharding-sample)] Cluster Node [akka.tcp://sharding-sample@sharding-sample-seeds-1.seeds-remoting:2551] - Node [akka.tcp://sharding-sample@sharding-sample-seeds-2.seeds-remoting:2551] is JOINING, roles []
```

If we now access the HTTP server through the proxy, we can see that the other nodes are being used by the cluster sharding.

```
> curl http://localhost:8001/api/v1/proxy/namespaces/default/pods/sharding-sample-seeds-0/customer/4712/address
Server sharding-sample-seeds-0:8080 replying:
Customer: 4712
Address: Some Street 48
Actor: akka.tcp://sharding-sample@sharding-sample-seeds-1.seeds-remoting:2551/system/sharding/Customer/12/4712
```

You can exchange the `sharding-sample-seeds-0` part of the URL with any other of the running pod names to access that particular pod.

### Starting the akka cluster member nodes

To add some akka cluster member nodes we need to create a deployment.

```
> kubectl apply -f k8s/sharding-sample-members.yaml
deployment "sharding-sample-members" created
```

The sharding-sample-members deployment defaults to creating one pod, and you can check that it has started in the same way as with the sharding-sample-seeds pods.

```
> kubectl get pods
NAME                                       READY     STATUS    RESTARTS   AGE
sharding-sample-members-3998602263-1ld9b   1/1       Running   0          35s
sharding-sample-seeds-0                    1/1       Running   0          10m
sharding-sample-seeds-1                    1/1       Running   0          5m
sharding-sample-seeds-2                    1/1       Running   0          4m
```

### Scaling the akka cluster member nodes

The akka cluster members can of course be scaled in the same way as the akka cluster seeds.

```
> kubectl scale deployment sharding-sample-members --replicas 4
deployment "sharding-sample-members" scaled
```

And we should have four member pods starting up.

```
> kubectl get pods
NAME                                       READY     STATUS    RESTARTS   AGE
sharding-sample-members-3998602263-1ld9b   1/1       Running   0          5m
sharding-sample-members-3998602263-8d553   1/1       Running   0          2m
sharding-sample-members-3998602263-dj8m1   1/1       Running   0          1m
sharding-sample-members-3998602263-gnsr7   0/1       Pending   0          0s
sharding-sample-seeds-0                    1/1       Running   0          15m
sharding-sample-seeds-1                    1/1       Running   0          10m
sharding-sample-seeds-2                    1/1       Running   0          9m
```

Please note that one or more pods might be stuck in pending, and not fully starting, depending on the amount of memory and cpu resources that you have allocated to Minikube using the `start` command.

### Exposing the HTTP servers as a Service

Now that the akka cluster is up and running, it is time to expose the HTTP servers as a service.

```
> kubectl apply -f k8s/sharding-sample-http.yaml
service "sharding-sample-http" created
```

This creates a service within the Kubernetes cluster that will route HTTP traffic to any of the nodes in the akka cluster. To be able to interact with it, we need to know where Minikube has exposed that service.

```
> minikube service sharding-sample-http --url
http://192.168.64.5:30786
```

To make it easier to call the HTTP service, we'll create an environment variable.

```
> export HTTP_SERVICE="$(minikube service sharding-sample-http --url)"
```

And then we can use that to call the service.

```
> curl "$HTTP_SERVICE/customer/4711/address"
Server 172.17.0.7:8080 replying:
Customer: 4711
Address: Some Street 20
Actor: akka.tcp://sharding-sample@sharding-sample-seeds-0.seeds-remoting:2551/system/sharding/Customer/11/4711
```

If you call the service several times, you will see that there are different HTTP servers handling the request, but the actor supplying the response is always the one being routed to by the akka cluster sharding.

### Cleaning up

To clean up the sample and stop running the pods, you just need to delete the deployment, stateful set, and services that we have created.

```
> kubectl delete deployment sharding-sample-members
deployment "sharding-sample-members" deleted
> kubectl delete statefulset sharding-sample-seeds
statefulset "sharding-sample-seeds" deleted
> kubectl delete service sharding-sample-http
service "sharding-sample-http" deleted
> kubectl delete service seeds-remoting
service "seeds-remoting" deleted
```

[curl]: https://curl.haxx.se
[docker]: https://www.docker.com
[java8]: http://www.oracle.com/technetwork/pt/java/javase/downloads/jdk8-downloads-2133151.html
[k8s-kubectl]: https://kubernetes.io/docs/tasks/tools/install-kubectl/
[k8s-deployment]: https://kubernetes.io/docs/concepts/workloads/controllers/deployment/
[k8s-service]: https://kubernetes.io/docs/concepts/services-networking/service/
[k8s-statefulset]: https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/
[minikube]: https://github.com/kubernetes/minikube#minikube
[sbt]: http://www.scala-sbt.org
[virtualbox]: https://www.virtualbox.org
[xhyve]: http://www.xhyve.org/
