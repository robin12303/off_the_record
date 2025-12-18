
---

# Project Name

**off_the_record / redteam2**

## Description

A toy project where a **C++ agent collects system events**, sends them to a **Java (Spring Boot) backend**, and visualizes them in a **Vue-based dashboard**.

## Overview

**Purpose**
A learning-oriented project focused on implementing an end-to-end event collection and transmission pipeline on Windows.

**Architecture**

* Agent (C++)
* Backend (Java)
* Dashboard (Vue)

**Status**
In progress / Experimental

## Features

### C++ Agent

* Collects system-level events on Windows
* Serializes events into JSON
* Sends data to the backend over the network

### Java Backend

* Receives events via WebSocket
* Manages agent connections and health checks
* Persists data using a relational database

### Vue Dashboard

* Displays incoming events in real time
* Shows agent connection and status information

## Tech Stack

**Agent**

* C++ (WinAPI, `std::jthread`, semaphores)

**Backend**

* Java 17
* Spring Boot
* WebSocket
* JPA / Flyway
* MySQL

**Frontend**

* Vue

## Getting Started

### Requirements

* Windows (for the agent)
* JDK 17 or higher
* Node.js with npm or pnpm
* MySQL (optional, if database persistence is enabled)

--- 
