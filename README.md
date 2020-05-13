# cf-up-and-running

Implementation using AWS CloudFormation of the examples from the book [Terraform: Up & Running](https://www.terraformupandrunning.com/)

## Usage

Create an IAM User, download [awscli](https://aws.amazon.com/cli/) and configure it:

```bash
$ aws configure
```

There are examples on how to use each component in the namespaces under cf-up-and-running.components.
 Eg: [cf-up-and-running.components.cloud-formation](src/cf_up_and_running/components/cloud_formation.clj).
 The examples are in the end of the files.

## Testing

This lib is mostly a wrapper over the java aws sdk and interacts. There is no tests because there is very little
 logic implemented in this lib. Whenever I add any logic I'll also add tests. In the meantime the changed code should
 be tested by running the components methods affected by the changes and check the results at repl and AWS console.

## License

Copyright © 2020 Claynon Ellert de Souza
