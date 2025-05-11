<p align="center">
 <img width="100px" src="https://github.com/likespro.png" align="center" alt="MasterID Server" />
 <h2 align="center">LikesPro Function Call Protocol</h2>
 <p align="center">A Kotlin library to make functions available as server methods in just one annotation.</p>
</p>
<p align="center">
    <a href="https://github.com/likespro/lpfcp/graphs/contributors">
      <img alt="GitHub Contributors" src="https://img.shields.io/github/contributors/likespro/lpfcp" />
    </a>
    <a href="https://github.com/likespro/lpfcp/issues">
      <img alt="Issues" src="https://img.shields.io/github/issues/likespro/lpfcp?color=0088ff" />
    </a>
    <a href="https://github.com/likespro/lpfcp/pulls">
      <img alt="GitHub pull requests" src="https://img.shields.io/github/issues-pr/likespro/lpfcp?color=0088ff" />
    </a>
  </p>
<p align="center">
    <a href="https://github.com/likespro/lpfcp/actions/workflows/main-branch.yml">
      <img alt="Build Passing" src="https://github.com/likespro/lpfcp/workflows/Main Branch Workflow/badge.svg" />
    </a>
    <a href="https://github.com/likespro/lpfcp">
      <img alt="Git Size" src="https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/likespro/lpfcp/badges/git-size.md" />
    </a>
    <a href="https://github.com/likespro/lpfcp">
      <img alt="Git File Count" src="https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/likespro/lpfcp/badges/git-file-count.md" />
    </a>
    <a href="https://github.com/likespro/lpfcp">
      <img alt="Git Lines Of Code" src="https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/likespro/lpfcp/badges/git-lines-of-code.md" />
    </a>
  </p>

## Quick Example

Shared
```kotlin
interface Calculator {
    fun add(a: Int, b: Int): Int
}
```

Server
```kotlin
class CalculatorImpl : Calculator {
    @LPFCP.ExposedFunction
    override fun add(a: Int, b: Int): Int = a + b
}

fun main() {
    val calculator = CalculatorImpl()
    lpfcpServer(calculator).start(wait = true)
}
```

Client
```kotlin
fun main() {
    val calculator = LPFCP.getProcessor<Calculator>(URI("http://localhost:8080/lpfcp"))
    println(calculator.add(1, 2)) // prints 3
}
```